package io.appservice.core.http;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.util.UUID;

import io.appservice.core.util.Logger;

public abstract class HttpRequestRestAsync extends HttpRequestAsync {

    private static final String LOG_TAG = "IOAPP_HttpRequestRestAsync";

    private Gson mGson;
    private String mResponse;

    public HttpRequestRestAsync() {
        mGson = new GsonBuilder().create();
    }

    public HttpRequestRestAsync(boolean insecure){
        super(insecure);
        mGson = new GsonBuilder().create();
    }

    protected abstract Object getRequest() throws Exception;

    @Override
    public void preprocess (HttpURLConnection con) throws Exception{
        Object request = getRequest();
        if ( request != null ) {
            con.setRequestMethod("POST");
            con.setRequestProperty("Content-Type", "application/json");
            con.setDoOutput(true);
            String body;
            if ( request instanceof String ){
                body = (String)request;
            }else {
                body = mGson.toJson(request);
            }
            Logger.i(LOG_TAG, "-> " + body);
            copy(new ByteArrayInputStream(body.getBytes()), con.getOutputStream());
            con.getOutputStream().flush();
        }
    }

    @Override
    public void process(HttpURLConnection con) throws Exception {
        if ( con.getResponseCode() == HttpURLConnection.HTTP_OK) {
            BufferedReader in = new BufferedReader(
                    new InputStreamReader(con.getInputStream()));
            String inputLine;
            StringBuffer response = new StringBuffer();

            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            in.close();
            mResponse = response.toString();
            Logger.i(LOG_TAG, "<- " + mResponse);
        }else{
            throw new RuntimeException("HTTP response code " + con.getResponseCode());
        }
    }

    public <T> T getResponse(Class<T> classOfT){
        return mGson.fromJson(mResponse, classOfT);
    }

    public String getResponse(){
        return mResponse;
    }
}
