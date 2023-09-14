package io.appservice.core.statemachine;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.appservice.core.statemachine.annotations.StateField;
import io.appservice.core.util.Logger;

public class StateContextStorage extends SQLiteOpenHelper {

    private static final String LOG_TAG = "IOAPP_StateContextStorage";

    private static final int DATABASE_VERSION = 1;
    private static final String DATABASE_NAME = "StateContextStorage.db";

    private static final String TABLE_CONTEXT = "context";
    private static final String TABLE_TIMER = "timer";
    private static final String TABLE_VALUE = "value";

    private static final String COLUMN_TYPE = "type";
    private static final String COLUMN_STATE = "state";
    private static final String COLUMN_TIMESTAMP = "timestamp";
    private static final String COLUMN_ID = "id";
    private static final String COLUMN_VALUE = "value";

    private static final String CREATE_DATABASE_SQL[] = {
            "CREATE TABLE " + TABLE_CONTEXT + " (type TEXT PRIMARY KEY, state INTEGER NOT NULL, timestamp INTEGER64 NOT NULL);\n",
            "CREATE TABLE " + TABLE_TIMER + " (type TEXT, id TEXT NOT NULL, timestamp INTEGER NOT NULL);\n",
            "CREATE TABLE " + TABLE_VALUE + " (type TEXT, id TEXT NOT NULL, value TEXT);"
    };

    private static final String DROP_DATABASE_SQL[] = {
            "DROP TABLE " + TABLE_CONTEXT + ";\n",
            "DROP TABLE " + TABLE_TIMER + ";\n",
            "DROP TABLE " + TABLE_VALUE + ";"
    };


    private Map<Class <? extends StateContext> , StateContextHolder> mContexts = new HashMap<>();

    private SQLiteDatabase mDB;

    public StateContextStorage(Context context, int version) {
        super(context, DATABASE_NAME, null, version);
        mDB = getWritableDatabase();
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        for (String sql : CREATE_DATABASE_SQL) {
            db.execSQL(sql);
        }
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if ( oldVersion != newVersion ) {
            for (String sql : DROP_DATABASE_SQL) {
                db.execSQL(sql);
            }
            onCreate(db);
        }
    }

    public <T extends StateContext> T load (Class <? extends StateContext > type){
        StateContextHolder holder = new StateContextHolder(type);
        T context = holder.load(mDB);
        if ( context != null ){
            mContexts.put(type, holder);
        }
        return context;
    }

    public void save (StateContext context){
        StateContextHolder holder = mContexts.get(context.getClass());
        if ( holder == null ){
            return;
        }
        holder.save(context, mDB);
    }



    private static class StateContextHolder {
        private Gson mGson = new GsonBuilder().create();
        private Map<String, Integer> mValues = new HashMap<>();
        private List<Field> mFields = new ArrayList<>();
        private Map<String, Long> mTimers = new HashMap<>();
        private Class<? extends StateContext> mContextType;


        private StateContextHolder(Class<? extends StateContext> type) {
            mContextType = type;
        }

        private int getHashCode(Object value) {
            if (value == null) {
                return 0;
            }
            return value.hashCode();
        }

        private void save(StateContext context, SQLiteDatabase db) {
            //save context
            {
                ContentValues values = new ContentValues();
                values.put(COLUMN_STATE, context.mCurrentState);
                int rec = db.update(TABLE_CONTEXT, values, COLUMN_TYPE + "=?", new String[]{mContextType.getName()});
                if (rec == 0) {
                    values.put(COLUMN_TYPE, mContextType.getName());
                    values.put(COLUMN_TIMESTAMP, 0);
                    db.insert(TABLE_CONTEXT, null, values);
                }
            }
            //save timers
            {
                for (Map.Entry<String, Long> timer : context.mTimers.entrySet()) {
                    if (mTimers.containsKey(timer.getKey())) {
                        if (!timer.getValue().equals(mTimers.get(timer.getKey()))) {
                            //update
                            ContentValues values = new ContentValues();
                            values.put(COLUMN_TIMESTAMP, timer.getValue());
                            db.update(TABLE_TIMER, values, COLUMN_TYPE + "=? AND " + COLUMN_ID + "=?", new String[]{mContextType.getName(), timer.getKey()});
                            mTimers.put(timer.getKey(), timer.getValue());
                        }
                    } else {
                        //insert
                        ContentValues values = new ContentValues();
                        values.put(COLUMN_TYPE, mContextType.getName());
                        values.put(COLUMN_ID, timer.getKey());
                        values.put(COLUMN_TIMESTAMP, timer.getValue());
                        db.insert(TABLE_TIMER, null, values);
                        mTimers.put(timer.getKey(), timer.getValue());
                    }
                }
                List < String > tm_remove = null;
                for (String timer_id : mTimers.keySet()) {
                    if (!context.mTimers.containsKey(timer_id)) {
                        db.delete(TABLE_TIMER, COLUMN_TYPE + "=? AND " + COLUMN_ID + "=?", new String[]{mContextType.getName(), timer_id});
                        if ( tm_remove == null ){
                            tm_remove = new ArrayList<>();
                        }
                        tm_remove.add(timer_id);
                    }
                }
                if ( tm_remove != null ){
                    for ( String tm: tm_remove){
                        mTimers.remove(tm);
                    }
                }
            }
            //save values
            {
                try {
                    for (Field field : mFields) {
                        Object value = field.get(context);
                        int hashCode = getHashCode(value);
                        if (mValues.containsKey(field.getName())) {
                            if (value == null) {
                                mValues.remove(field.getName());
                                Logger.d(LOG_TAG, "Removed field " + context.getClass().getName() + " " + field.getName());
                                db.delete(TABLE_VALUE, COLUMN_TYPE + "=? AND " + COLUMN_ID + "=?", new String[]{mContextType.getName(), field.getName()});
                            } else if (!mValues.get(field.getName()).equals(hashCode)) {
                                //update if not match
                                ContentValues values = new ContentValues();
                                values.put(COLUMN_VALUE, mGson.toJson(value));
                                db.update(TABLE_VALUE, values, COLUMN_TYPE + "=? AND " + COLUMN_ID + "=?", new String[]{mContextType.getName(), field.getName()});
                                Logger.d(LOG_TAG, "Update field " + context.getClass().getName() + " " + field.getName() + " old=" + mValues.get(field.getName()) + " new=" + value);
                                mValues.put(field.getName(), hashCode);
                            }
                        } else {
                            ContentValues values = new ContentValues();
                            values.put(COLUMN_TYPE, mContextType.getName());
                            values.put(COLUMN_ID, field.getName());
                            values.put(COLUMN_VALUE, mGson.toJson(value));
                            db.insert(TABLE_VALUE, null, values);
                            Logger.d(LOG_TAG, "Insert field " + context.getClass().getName() + " " + field.getName() + " new=" + value);
                            mValues.put(field.getName(), hashCode);
                        }
                    }
                } catch (Exception e) {
                    db.delete(TABLE_CONTEXT, COLUMN_TYPE + "=?", new String[]{mContextType.getName()});
                    db.delete(TABLE_TIMER, COLUMN_TYPE + "=?", new String[]{mContextType.getName()});
                    db.delete(TABLE_VALUE, COLUMN_TYPE + "=?", new String[]{mContextType.getName()});
                }
            }
        }

        private <T extends StateContext> T load(SQLiteDatabase db) {
            Field[] fields = mContextType.getDeclaredFields();
            for (Field field : fields) {
                if (field.isAnnotationPresent(StateField.class)) {
                    field.setAccessible(true);
                    mFields.add(field);
                }
            }

            T state_ctx;
            try {
                state_ctx = (T)mContextType.newInstance();
            } catch (Exception e) {
                Logger.e(LOG_TAG, "Could not create context " + e.getMessage());
                return null;
            }
            Cursor context = db.query(TABLE_CONTEXT,
                    new String[]{COLUMN_STATE},
                    COLUMN_TYPE + "=?",
                    new String[]{mContextType.getName()},
                    null,
                    null,
                    null);
            if (context.moveToNext()) {
                state_ctx.mCurrentState = context.getInt(0);
                context.close();
                Cursor timer = db.query(TABLE_TIMER,
                        new String[]{COLUMN_ID, COLUMN_TIMESTAMP},
                        COLUMN_TYPE + "=?",
                        new String[]{mContextType.getName()},
                        null,
                        null,
                        null);
                while (timer.moveToNext()) {
                    String timer_id = timer.getString(0);
                    Long timer_timestamp = timer.getLong(1);
                    mTimers.put(timer_id, timer_timestamp);
                    state_ctx.mTimers.put(timer_id, timer_timestamp);
                }
                timer.close();
                Cursor value = db.query(TABLE_VALUE,
                        new String[]{COLUMN_ID, COLUMN_VALUE},
                        COLUMN_TYPE + "=?",
                        new String[]{mContextType.getName()},
                        null,
                        null,
                        null);
                while (value.moveToNext()) {
                    String field_name = value.getString(0);
                    String field_value = value.getString(1);
                    try {
                        Field field = mContextType.getDeclaredField(field_name);
                        field.setAccessible(true);
                        Object field_object = mGson.fromJson(field_value, field.getType());
                        field.set(state_ctx, field_object);
                        Logger.d(LOG_TAG, "Loaded field " + mContextType.getName() + " " + field_name + "=" + field_object);
                        mValues.put(field_name, getHashCode(field_object));
                    } catch (Exception e) {
                        Logger.e(LOG_TAG, "Could not set field " + field_name + " - " + e.getMessage());
                    }
                }
                value.close();
            } else {
                context.close();
            }
            return state_ctx;
        }
    }
}
