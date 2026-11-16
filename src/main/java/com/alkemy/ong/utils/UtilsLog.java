package com.alkemy.ong.utils;

import com.alkemy.ong.enums.TipLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class UtilsLog {

    public static void registrarInfo(Class<?> clase, TipLog tip, String message)
    {
        Logger log = LoggerFactory.getLogger(clase);

        switch (tip)
        {
            case DEBUG:
                log.debug(message);
                break;
            case ERROR:
                log.error(message);
                break;
            case FATAL:
                log.error(message);
                break;
            case INFO:
                log.info(message);
                break;
            case WARNING:
                log.warn(message);
        }
    }
}
