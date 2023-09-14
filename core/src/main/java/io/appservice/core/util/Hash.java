package io.appservice.core.util;

public class Hash {
    public static int calc(Object ... values){
        int hashCode = 1;
        for ( Object obj: values){
            hashCode = 31*hashCode + (obj==null ? 0 : obj.hashCode());
        }
        return hashCode;
    }
}
