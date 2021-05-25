package com.abouttimetech.weatherupdater;


import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

import org.dom4j.Document;
import org.dom4j.DocumentFactory;
import org.dom4j.io.SAXReader;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

/** Class: YahooWeatherParser
 *  Description: Weather parsing class from Yahoo weather services.
 *  @author Russ
 *  Copyright 2010. All rights reserved About Time Technologies, L.L.C.
 */
public class YahooWeatherParser {
	boolean yahoo = false;
	
	public Weather parse(InputStream inputStream) throws Exception {
	    Weather weather = new Weather();
	  
	    SAXReader xmlReader = createXmlReader();
	    Document doc = xmlReader.read( inputStream );
	
	    weather.setCity( doc.valueOf("/rss/channel/y:location/@city") );
	    weather.setRegion( doc.valueOf("/rss/channel/y:location/@region") );
	    weather.setCountry( doc.valueOf("/rss/channel/y:location/@country") );
	    weather.setCondition( doc.valueOf("/rss/channel/item/y:condition/@text") );
	    weather.setTemp( doc.valueOf("/rss/channel/item/y:condition/@temp") );
	    weather.setChill( doc.valueOf("/rss/channel/y:wind/@chill") );
	    weather.setHumidity( doc.valueOf("/rss/channel/y:atmosphere/@humidity") );
	    weather.setPressure( doc.valueOf("/rss/channel/y:atmosphere/@pressure") );
	    weather.setVisibility( doc.valueOf("/rss/channel/y:atmosphere/@visibility") );
	    weather.setWindSpeed( doc.valueOf("/rss/channel/y:wind/@speed") );
	  
	    return weather;
	}

	private SAXReader createXmlReader() {
		Map<String,String> uris = new HashMap<String,String>();
		if(yahoo)
		{
			uris.put( "y", "http://xml.weather.yahoo.com/ns/rss/1.0" );
		}
		else
		{
			uris.put( "abtt_dev", "api.openweathermap.org/data/2.5/forecast?id=524901&appid={b5a56faf31762cd439f74d19585a7db4}" );
		}
        
		DocumentFactory factory = new DocumentFactory();
		factory.setXPathNamespaceURIs( uris );
        
		SAXReader xmlReader = new SAXReader();
		xmlReader.setDocumentFactory( factory );
		return xmlReader;
  	}
	
	   /** Parse the given InputStream as JSON, extracting weather info
     *  Create and init Weather object
     *  
     * @param inputStream
     * @return
     * @throws Exception
     */
    public static Weather parseWeatherJson(InputStream inputStream) throws Exception {
        Weather weather = null;
        JsonObject response = parseJsonStream(inputStream);
        if (response.has("location")) {
            try {
                JsonObject location = response.get("location").getAsJsonObject();
                JsonObject atmosphere = null;
                JsonObject wind = null;
                JsonObject condition = null;
                JsonObject currObservation = response.get("current_observation").getAsJsonObject();
                atmosphere = currObservation.get("atmosphere").getAsJsonObject();
                wind = currObservation.get("wind").getAsJsonObject();
                condition = currObservation.get("condition").getAsJsonObject();
                
                weather = new Weather();
                weather.setCity( getAsString(location.get("city")) );
                weather.setRegion( getAsString(location.get("region")) );
                weather.setCountry( getAsString(location.get("country")) );
                weather.setCondition( getAsString(condition.get("text")) );
                weather.setTemp( getAsString(condition.get("temperature")) );
                weather.setChill( getAsString(wind.get("chill")) );
                weather.setHumidity( getAsString(atmosphere.get("humidity")) );
                weather.setPressure( getAsString(atmosphere.get("pressure")) );
                weather.setVisibility( getAsString(atmosphere.get("visibility")) );
                weather.setWindSpeed( getAsString(wind.get("speed")) );
            } catch (NullPointerException e) {
                // Ignore empty response {"location":{},"current_observation":{},"forecasts":[]} parsing errors
            }
        }

        return weather;
    }
    
    /** Parse the given InputStream as JSON, extracting weather info
     *  Create and init Weather object
     *  
     * @param inputStream
     * @return
     * @throws Exception
     */
    public static Weather parseWeatherJsonNew(InputStream inputStream) throws Exception {
        Weather weather = null;
        JsonObject response = parseJsonStream(inputStream);
        if (response.has("location")) {
            try {
                JsonObject location = response.get("location").getAsJsonObject();
                JsonObject atmosphere = null;
                JsonObject wind = null;
                JsonObject condition = null;
                JsonObject currObservation = response.get("current").getAsJsonObject();
                //atmosphere = currObservation.get("atmosphere").getAsJsonObject();
                //wind = currObservation.get("wind_mph").getAsJsonObject();
                condition = currObservation.get("condition").getAsJsonObject();
                
                weather = new Weather();
                weather.setCity( getAsString(location.get("name")) );
                weather.setRegion( getAsString(location.get("region")) );
                weather.setCountry( getAsString(location.get("country")) );
                weather.setCondition( getAsString(condition.get("text")) );
                weather.setTemp( getAsString(currObservation.get("temp_f")) );
                weather.setChill( getAsString(currObservation.get("feelslike_f")) );
                weather.setHumidity( getAsString(currObservation.get("humidity")) );
                weather.setPressure( getAsString(currObservation.get("pressure_in")) );
                weather.setVisibility( getAsString(currObservation.get("vis_miles")) );
                weather.setWindSpeed( getAsString(currObservation.get("wind_mph")) );
            } catch (NullPointerException e) {
                // Ignore empty response {"location":{},"current_observation":{},"forecasts":[]} parsing errors
            }
        }

        return weather;
    }


    /**
     * Parse the given InputStream as JSON, extracting response JSON
     * @param inputStream
     * @return
     * @throws Exception
     */
    private static JsonObject parseJsonStream(InputStream inputStream) {
        JsonObject response = null;
        
        try {
            BufferedReader in = new BufferedReader(new InputStreamReader(inputStream));

            String resultLine;
            StringBuffer resultJsonString = new StringBuffer();
            while ((resultLine = in.readLine()) != null) {
                resultJsonString.append(resultLine);
            }
            in.close();

            JsonParser parser = new JsonParser();
            JsonElement data = parser.parse(resultJsonString.toString());
            if (data != null && data.isJsonObject()) {
                response = data.getAsJsonObject();
            }
          } catch (JsonSyntaxException e) {
              e.printStackTrace();
          } catch (IOException e) {
              e.printStackTrace();
          }
        return response != null ? response : new JsonObject();
    }

	/** Parse the given InputStream as JSON.
	 *  Return the "channel" object if it exists, otherwise, return an empty JsonObject
	 *  Deprecated due to change in JSON format with new API
	 * @param inputStream
	 * @return
	 */
    @Deprecated
    private static JsonObject parseJsonStreamOld(InputStream inputStream) {
	    JsonObject channel = null;
	    
        try {
            BufferedReader in = new BufferedReader(new InputStreamReader(inputStream));

            String resultLine;
            StringBuffer resultJsonString = new StringBuffer();
            while ((resultLine = in.readLine()) != null) {
                resultJsonString.append(resultLine);
            }
            in.close();

            JsonParser parser = new JsonParser();
            JsonElement data = parser.parse(resultJsonString.toString());
            if (data != null && data.isJsonObject()) {
                JsonElement queryElem = data.getAsJsonObject().get("query");
                if (queryElem != null && queryElem.isJsonObject()) {
                    JsonElement results = queryElem.getAsJsonObject().get("results");
                    if (results != null && results.isJsonObject()) {
                        channel = results.getAsJsonObject().get("channel").getAsJsonObject();
                    }
                }
            }
        } catch (JsonSyntaxException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return channel != null ? channel : new JsonObject();
	}
	
    public static String getAsString(JsonElement jsonElem) {
        String value = "";
        
        if ((jsonElem != null) &&
            (jsonElem.isJsonPrimitive())) {
            value = jsonElem.getAsString();
        }
        return value;
    }
}
