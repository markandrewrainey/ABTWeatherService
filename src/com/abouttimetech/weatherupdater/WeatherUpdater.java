package com.abouttimetech.weatherupdater;

import java.io.*;
import java.net.*;
import java.sql.*;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Base64.Encoder;
import java.util.Date;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.net.ssl.HttpsURLConnection;

//import oauth.signpost.OAuthConsumer;
//import oauth.signpost.basic.DefaultOAuthConsumer;
//import oauth.signpost.exception.OAuthCommunicationException;
//import oauth.signpost.exception.OAuthExpectationFailedException;
//import oauth.signpost.exception.OAuthMessageSignerException;

import com.microsoft.sqlserver.jdbc.SQLServerDataSource;

public class WeatherUpdater extends Thread {

    private static String consumer_key = null;
    private static String consumer_secret = null;
//    private OAuthConsumer consumer;

	/**
	 * @param args
	 */
	Time startTime;
	int updateInterval;
	Time endTime;
	private boolean runWeatherThread;
	
	private Map<String, Weather> weatherMap;
	
	public WeatherUpdater(){
		
	}
	
	public void run(){
		System.out.println("Checkpoint run start : runWeatherThread = " +runWeatherThread);
		WeatherThreadRunning();	
		System.out.println("Checkpoint before while of run : runWeatherThread = " +runWeatherThread);
		while (runWeatherThread){
			
		    String city;
		    String state;
		    String zip;
		    String country;
		    String locationParams;
			String woeid;
			int jobId;
			String jobName;
			String jobCode;
			int visibility;
			int temperature;
			int windChill;
			int humidity;
			int windSpeed;
			Connection currentConnection;
			
			currentConnection = ConnectToATSQLDB(getDBName());
			
			weatherMap = new HashMap<String, Weather>(); // New map each refresh cycle, always refresh 1st time
			
			// If we need to support oAuth, then create columns and call    loadApiKeys(currentConnection);
			ResultSet ActiveJobs = getActiveJobs(currentConnection);
			
			//Get Active Jobs from Job table, parse data, and enter into Daily Job Weather table
			try {
				while (ActiveJobs.next()) {
					//get job data active jobs from the job table
				    city = ActiveJobs.getString("city");
				    state = ActiveJobs.getString("state");
				    zip = ActiveJobs.getString("zip");
				    country = ActiveJobs.getString("country");
				    locationParams = getLocationParams(city, state, zip, country);
					woeid = ActiveJobs.getString("woeid");
					jobId = ActiveJobs.getInt("jobId");
					jobCode = ActiveJobs.getString("jobCode");
					String ajJobName = ActiveJobs.getString("jobName");
					jobName = ajJobName.replace("'", "''");
					
					Weather weather = getWeather(woeid, locationParams);
					if (weather != null){
    					System.out.println(weather.getVisibility());
    				    System.out.println("Weather Data set for " +jobName+ " and " +jobCode+ ".");
    				    
    					
    				    //Get the weather data to enter into the table
    				    if (weather.getTemp() != ""){
    				    	String strTemp = weather.getTemp(); 
    				    	temperature = Double.valueOf(strTemp).intValue();;
    				    }
    				    else{
    				    	temperature = -500;
    				    }
    				    
    				    if (weather.getChill() != ""){
    				    	String strChill = weather.getChill();
    				    	windChill = Double.valueOf(strChill).intValue();
    				    }
    				    else{
    				    	windChill = -500;
    				    }
    				    
    				    if (weather.getHumidity() != ""){
    				    	String strHumidity = weather.getHumidity();
    				    	humidity = Double.valueOf(strHumidity).intValue();
    				    }
    				    else{
    				    	humidity = -500;
    				    }
    				    
    				    if (weather.getVisibility() != "") {
    				    	String strVisibility = weather.getVisibility();
    				    	visibility = Double.valueOf(strVisibility).intValue();
    				    }
    				    else{
    				    	visibility = -500; 
    				    }
    				    
    				    if (weather.getWindSpeed() != ""){
    				    	String strWindSpeed = weather.getWindSpeed();
    				    	windSpeed = Double.valueOf(strWindSpeed).intValue();
    				    }
    				    else{
    				    	windSpeed = -500;
    				    }
    				    
    				    String conditions = weather.getCondition();
    				    
    				   	System.out.println(weather.getTemp()+ "," +weather.getChill()+ "," +weather.getHumidity()+ "," +weather.getWindSpeed()+ ", " +weather.getCondition()+ ", visibility = " +weather.getVisibility());
    				    				    
    				    //Get the system date and time.
    				    java.util.Date utilDate = new Date();
    				    // Convert it to java.sql.Date
    				    java.sql.Timestamp sqlTimestamp = new java.sql.Timestamp(utilDate.getTime());
    				    
    				    //enter the data in the table
    				    currentConnection.createStatement().executeUpdate("INSERT INTO DailyJobWeather (jobId, jobName, updateTime, temperature, windChill, humidity, windSpeed, conditions, visibility) " +
    				    		"VALUES ('" +jobId+ "', '" +jobName+ "', '" +sqlTimestamp+ "', '" +temperature+ "', '" +windChill+ "', '" +humidity+"', '" +windSpeed+ "', '" +conditions+ "', '" +visibility+ "')");
    					
    				    System.out.println("Data Inserted");
					}
					else{
					    System.out.println("No WOEID to process job: " + jobName);
	                }
				}
			} 
			catch (SQLException e) {
				e.printStackTrace();
				System.out.println("There was a SQL Exception Error");
			} 
			catch (Exception e) {
				e.printStackTrace();
			}

			DisconnectFromATSQLDB(ConnectToATSQLDB(getDBName()));
			System.out.println("Database Connection Closed");
			System.out.println("Thread is sleeping");
			
			long timeToSleep = calculateTimeToSleep();
			
			//sleep the thread for 30 minutes
			try {
				this.sleep(timeToSleep);
			} 
			catch (InterruptedException e) {
				e.printStackTrace();
			}
			
			WeatherThreadRunning();
		}
	}

	private Weather getWeather(String woeid, String locationParams) {
	    Weather weather = null;
	    String webArg = getWebArg(woeid, locationParams);
	    
	    if (!webArg.isEmpty()) {
	        // First, check cache to see if we have recently retrieved weather for location
	        weather = weatherMap.get(webArg);
	        
	        if (weather == null) {
	            if (webArg.equals(woeid)) {
	                weather = getWeatherFromServerRequest("woeid", webArg);
	            } else {
	                weather = getWeatherFromServerRequest("location", webArg);
	            }
	            if (weather != null) {
	                weatherMap.put(webArg, weather);
	            }
	        }
	    }
        
        return weather;
	}
	
	/** Get the key representing the Weather results */
	private String getWebArg(String woeid, String locationParams) {
	    String webArg = "";
	    if (woeid != null && woeid.length() > 0){
	        webArg = woeid;
	    }
	    else if (locationParams != null && !locationParams.isEmpty()) {
	        webArg = locationParams;
	    }
	    return webArg;
	}
	
	@Deprecated
	private Weather getWeatherFromServer(String zip, String woeid) {
	    Weather weather = null;
	    //retrieve weather data for item from website
	    try {
	        InputStream websiteWeatherData = retrieveYqlWeather(zip, woeid);
	        if (websiteWeatherData != null){
	            //parse data from xml format into Weather Class
	            weather = new YahooWeatherParser().parseWeatherJson(websiteWeatherData);
	            //close the input stream from website
	            websiteWeatherData.close();
	            websiteWeatherData = null;
	            System.out.println("Yahoo input stream closed");
	        }
	    } catch (IOException e) {
	        e.printStackTrace();
	    } catch (Exception e) {
	        e.printStackTrace();
	    }
	    return weather;
	}

	/** Old rss feed version, parses XML to populate Weather object */
	@Deprecated
	private Weather getWeatherFromServerRss(String zip, String woeid) {
	    Weather weather = null;
        //retrieve weather data for item from website
        try {
            InputStream websiteWeatherData = retrieveWebsiteWeather(getWebArg(woeid,""));
            if (websiteWeatherData != null){
                //parse data from xml format into Weather Class
                weather = new YahooWeatherParser().parse(websiteWeatherData);
                //close the input stream from website
                websiteWeatherData.close();
                websiteWeatherData = null;
                System.out.println("Yahoo input stream closed");
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return weather;
	}
	
	private String getDBName() {
		String DBName = null;
		
		//create an instance of properties class
		Properties properties = new Properties();
		
		 try 
	        {
		        properties.load(new FileInputStream("WeatherProperties.properties"));
		        DBName = properties.getProperty("database");
	         }

	        //catch exception in case properties file does not exist

	        catch(IOException e)
	        {
	        	e.printStackTrace();
	        	System.out.println("Unable to get database name.");
	        	//Write this error to the log file
	        }
		
		return DBName;
	}

	public static void main(String[] args) {
		
		
		(new WeatherUpdater()).start();
		
	}
	
	public static Connection ConnectToATSQLDB(String DBName){
		Connection conn = null;
		SQLServerDataSource database = new SQLServerDataSource();
		
		try {
			
			conn = DriverManager.getConnection(DBName);
			System.out.println("Connected to the database");
			
		} catch (SQLException e) {
			
			e.printStackTrace();
			System.out.println("There was an error connecting to the database.");
			//Write this error to the log file
		}
		
		return conn;
	}
	
	public static void DisconnectFromATSQLDB(Connection conn){
		
		try {
			conn.close();
		} catch (SQLException e) {
			
			e.printStackTrace();
			System.out.println("There was an error disconnecting from the database.");
			//Write this error to the log file.
		}
		
	}
	
		
		//Get the Update Interval from the properties file
		//private int updateInterval = Integer.parseInt(getUpdateInterval());
		
	public long calculateTimeToSleep(){
		
		int timeCompare = 0;
		long updateInterval = 1;
		int intTimesPerDay = 1;
		String startTime = null;
		String endTime = null;
		String timesPerDay = null;
		
		//create an instance of properties class
		Properties properties = new Properties();
		
			//try retrieve data from file
	        try 
	        {
		        properties.load(new FileInputStream("WeatherProperties.properties"));
		        timesPerDay = properties.getProperty("timesPerDay");
		        startTime = properties.getProperty("startTime");
		        endTime = properties.getProperty("endTime");
		        System.out.println("updateInterval = " +updateInterval+ ", startTime = " +startTime+ ", endTime = " +endTime);
	         
		        intTimesPerDay = Integer.parseInt(timesPerDay);
		        
	        }
	        catch(IOException e)
	        {
	        	e.printStackTrace();
	        	System.out.println("Unable to get properties file data.");
	        	//Write this error to the log file
	        }
	        
			
			SimpleDateFormat sdf = new SimpleDateFormat("HH:mm");
			Calendar currentCalendar = Calendar.getInstance();
			//
			
			//Date currentTime = currentCalendar.getTime();
			int hour = currentCalendar.get(Calendar.HOUR_OF_DAY);
			int min = currentCalendar.get(Calendar.MINUTE);
			
			String time = Integer.toString(hour) + ":" + Integer.toString(min);
			
			System.out.println(time);
	        
	        try {
	            Date dateEndTime = sdf.parse(endTime);
	            Date dateStartTime = sdf.parse(startTime);
	            timeCompare = dateEndTime.compareTo(dateStartTime);
	            System.out.println("TimeCompare=" +timeCompare);
	            long timeDifference = dateEndTime.getTime() - dateStartTime.getTime();
	            System.out.println("TimeDifference = " +timeDifference);
	            updateInterval = timeDifference / intTimesPerDay;
	            System.err.println("updateInterval = " +updateInterval);
	        }
	        
	        //catch exception in case properties file does not exist
	        catch (ParseException e){
	            // Exception handling goes here
	        }
	        
	        
	     return updateInterval;
       
	}
	
	public String getEndTime(){
		
		String endTime = null;
				
		//create an instance of properties class
		Properties properties = new Properties();
		
			//try retrieve data from file
	        try 
	        {
		        properties.load(new FileInputStream("WeatherProperties.properties"));
		        endTime = properties.getProperty("endTime");
		        System.out.println("End Time =" +endTime);
	         }

	        //catch exception in case properties file does not exist

	        catch(IOException e)
	        {
	        	e.printStackTrace();
	        	System.out.println("Unable to get end time.");
	        	//Write this error to the log file
	        }
	        
	     return endTime;
       
	}		

	public ResultSet getActiveJobs(Connection conn)
	{
		ResultSet activeJobs = null;
		try 
		{
			activeJobs = conn.createStatement().executeQuery("select city, state, zip, country, woeid, jobId, jobName, jobCode from Job where active = 1");
		} 
		catch (SQLException e) 
		{			
			e.printStackTrace();
			System.out.println("The getActiveJob method failed.  The SELECT query could not be executed.");
			//Write this exception to the log file.
		}
		
		return activeJobs;
	}

	/**
	 * Make a yahoo API Request to get the weather forcast
	 * 'query' and params must be kept separate for use in oAuth signature generation
	 * Process JSON response as a weather response
	 * 
	 * @param query ("woeid" or "location")
	 * @param params (query params)
	 * @return Weather (or null if none returned)
	 */
    private Weather getWeatherFromServerRequest(String query, String params) {
        Weather weather = null;
        //retrieve weather data for item from website
        try {
            InputStream websiteWeatherData = yahooApiRequest(query, params);
            if (websiteWeatherData != null){
                //parse data from json format into Weather Class
                weather = YahooWeatherParser.parseWeatherJson(websiteWeatherData);
                //close the input stream from website
                websiteWeatherData.close();
                websiteWeatherData = null;
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return weather;
    }

    /** Generate Yahoo API Request "location" parameters */
    private String getLocationParams(String city, String state, String zip, String country) {
        String locationParams = "";
        if (city != null && !city.trim().isEmpty()) {
            locationParams = city.trim();
            if (state != null && !state.trim().isEmpty()) {
                locationParams += "," + state.trim();
            }
            else if (country != null && !country.trim().isEmpty()) {
                locationParams += "," + country.trim();
            }
        }
        return locationParams;
    }

	private InputStream retrieveYqlWeather(String zip, String woeid) throws Exception {
	    String yqlWhere = "";
	    String yqlParams = "";
	    String yqlUrl = "http://query.yahooapis.com/v1/public/yql?q=";
	    yqlParams = "select location, wind, atmosphere, item.condition from weather.forecast where ";
	    
        if (woeid != null && woeid.length() > 0){
            yqlWhere = "woeid = " + woeid;
        }
        else if (zip != null && zip.length() > 0){
            yqlWhere = "woeid in (select woeid from geo.places(1) where text=\"" + zip + "\")";
        }
	    
	    String formatParam = "&format=json";
	    
	    URL apiUrl = new URL(yqlUrl + URLEncoder.encode(yqlParams + yqlWhere , "UTF-8") + formatParam);
	    URLConnection conn = apiUrl.openConnection();
        if (consumer_key != null && !consumer_key.isEmpty() && 
                consumer_secret != null && !consumer_secret.isEmpty()) {
            // use oAuth
            signWithOAuthConsumer(conn);
        }
        return (conn != null) ? conn.getInputStream() : null;
	}
	
	private InputStream retrieveWebsiteWeather(String webArg) throws Exception {
        String url = null;
        if (webArg != null && webArg.length() > 0){
            url = "http://weather.yahooapis.com/forecastrss?" + webArg; // w={woeid} OR p={zipCode}
        }

        if (url != null){
            InputStream is;
            URLConnection conn = new URL(url).openConnection();
            if (consumer_key != null && !consumer_key.isEmpty() && 
                    consumer_secret != null && !consumer_secret.isEmpty()) {
                // use oAuth
                signWithOAuthConsumer(conn);
            }
            return (conn != null) ? conn.getInputStream() : null;
        }
        else{
            return null;
        }
	}

	/** Possible future use, load Yahoo Api key & secret from System table (columns don't yet exist) */
	private void loadApiKeys(Connection conn) {
	    try {
	        ResultSet rs = conn.createStatement().executeQuery("select yhApiKey, yhApiSecret from System"); // These don't exist yet
	        
	        if (rs.next()) {
	            consumer_key = rs.getString(1);
	            consumer_secret = rs.getString(2);
	        }
	    } catch (SQLException e) {
	        e.printStackTrace();
	    }
	}
	
	private void signWithOAuthConsumer(URLConnection conn) throws Exception {
	    if (conn == null) return;
	    
//		// Create oAuth Consumer 
//	    consumer = new DefaultOAuthConsumer(consumer_key, consumer_secret);
//
//	    if (consumer != null) {
//	        try {
//	            consumer.sign(conn);
//	            
//	        } catch (OAuthMessageSignerException e) {
//	            throw e;
//	        } catch (OAuthExpectationFailedException e) {
//	            throw e;
//	        } catch (OAuthCommunicationException e) {
//	            throw e;
//	        }
//	        conn.connect();
//	    }
	}

	public boolean runThread() {
		return runWeatherThread;
	}

	public void WeatherThreadRunning() {
	    boolean checkTime;
	    checkTime = true;
	    
	    while (checkTime){
		
    		System.out.println("Checkpoint 0 : runWeatherThread = " +runWeatherThread);
    		System.out.println("checking the time");
    		int timeCompare = 0;
    		
    		SimpleDateFormat sdf = new SimpleDateFormat("HH:mm");
    		Calendar currentCalendar = Calendar.getInstance();
    		String endTimeStr = getEndTime();
    		
    		//Date currentTime = currentCalendar.getTime();
    		int hour = currentCalendar.get(Calendar.HOUR_OF_DAY);
    		int min = currentCalendar.get(Calendar.MINUTE);
    		
    		String time = Integer.toString(hour) + ":" + Integer.toString(min);
    		
    		System.out.println(time);
            
            try {
                Date endTime = sdf.parse(endTimeStr);
                Date currentTime = sdf.parse(time);
                timeCompare = endTime.compareTo(currentTime);
                System.out.println("TimeCompare=" +timeCompare);
                long timeDifference = endTime.getTime() - currentTime.getTime();
                System.out.println("TimeDifference = " +timeDifference);
    
                // Outputs -1 as date1 is before date2
                // Outputs 1 as date1 is after date1      
                // Outputs 0 as the dates are now equal
                
                if (timeCompare >= 0)
                {
                	checkTime = false;
                    runWeatherThread = true;
                	System.out.println("Checkpoint 1 : runWeatherThread = " +runWeatherThread);
                }
                else
                {
                    checkTime = true;
                    //check the time again in 30 minutes
                    try {
                        this.sleep(1800000);
                        
                    } catch (InterruptedException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                	//runWeatherThread = false;
                	System.out.println("Checkpoint 2 : runWeatherThread = " +runWeatherThread);
                }
    
            } catch (ParseException e){
                // Exception handling goes here
            }
            
    	}
	}
	
	   /**
     * Yahoo API access / oAuth 1.0 Utility methods
     */
    
    final String appId = "LFoubk4a";
    final String consumerKey = "dj0yJmk9ZzczamtXcWJIbHZ0JnM9Y29uc3VtZXJzZWNyZXQmc3Y9MCZ4PWIy";
    final String consumerSecret = "c39b944a495eb345d8f6c24daadd6e5b492b2129";
    final String ydnUrl = "https://weather-ydn-yql.media.yahoo.com/forecastrss";

    /**
     * 01/29/2019
     * Hits new Yahoo weather endpoint (see https://developer.yahoo.com/weather/documentation.html)
     * oAuth1 protected API
     * 
     * @param query parameter ("location" or "woeid")
     * @param params (city name or woeid number)
     * @return
     */
    private InputStream yahooApiRequest(String query, String params) {
        InputStream response = null;
        try {
            URL url = URI.create(ydnUrl + "?" + query + "=" + URLEncoder.encode(params, "UTF-8") + "&format=json").toURL();
            
            HttpsURLConnection urlConn = (HttpsURLConnection)url.openConnection();
            urlConn.setDoInput (true);
            urlConn.setDoOutput (true);
            urlConn.setUseCaches (false);
            
            String authorizationLine = generateYahooApiAuthorization(query, params);
            urlConn.setRequestProperty ("Authorization", authorizationLine);
            urlConn.setRequestProperty ("X-Yahoo-App-Id", appId);
            urlConn.setRequestProperty ("Content-Type", "application/json");
            urlConn.setRequestMethod("GET");
            
            urlConn.connect();
            if (urlConn.getResponseCode() == HttpsURLConnection.HTTP_OK) {
                response = urlConn.getInputStream();
            } else {
                System.err.println(urlConn.getResponseCode() + " " + urlConn.getResponseMessage());
                System.err.println(url);
            }
        } catch (IOException e) {
            System.out.println(e.getMessage());
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        
        return response;
    }

    /**
     * Generate oAuth1 authorization property for the Yahoo API query
     * @param query parameter ("location" or "woeid")
     * @param params (city name or woeid number)
     * @return
     * @throws UnsupportedEncodingException 
     */
    private String generateYahooApiAuthorization(String query, String params) throws UnsupportedEncodingException {
        long timestamp = new Date().getTime() / 1000;
        byte[] nonce = new byte[32];
        Random rand = new Random();
        rand.nextBytes(nonce);
        String oauthNonce = new String(nonce).replaceAll("\\W", "");

        List<String> parameters = new ArrayList<>();
        parameters.add("oauth_consumer_key=" + consumerKey);
        parameters.add("oauth_nonce=" + oauthNonce);
        parameters.add("oauth_signature_method=HMAC-SHA1");
        parameters.add("oauth_timestamp=" + timestamp);
        parameters.add("oauth_version=1.0");
        // Make sure query value is encoded with %20 for spaces rather than "+"
        parameters.add(query + "=" + URLEncoder.encode(params, "UTF-8").replace("+", "%20"));
        parameters.add("format=json");
        Collections.sort(parameters);

        StringBuffer parametersList = new StringBuffer();
        for (int i = 0; i < parameters.size(); i++) {
            parametersList.append(((i > 0) ? "&" : "") + parameters.get(i));
        }

        String signatureString = "GET&" +
            URLEncoder.encode(ydnUrl, "UTF-8") + "&" +
            URLEncoder.encode(parametersList.toString(), "UTF-8");

        String signature = null;
        try {
            SecretKeySpec signingKey = new SecretKeySpec((consumerSecret + "&").getBytes(), "HmacSHA1");
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(signingKey);
            byte[] rawHMAC = mac.doFinal(signatureString.getBytes());
            Encoder encoder = Base64.getEncoder();
            signature = encoder.encodeToString(rawHMAC);
        } catch (Exception e) {
            System.err.println("Unable to append signature");
            System.exit(0);
        }

        String authorizationLine = "OAuth " +
            "oauth_consumer_key=\"" + consumerKey + "\", " +
            "oauth_nonce=\"" + oauthNonce + "\", " +
            "oauth_timestamp=\"" + timestamp + "\", " +
            "oauth_signature_method=\"HMAC-SHA1\", " +
            "oauth_signature=\"" + signature + "\", " +
            "oauth_version=\"1.0\"";

        return authorizationLine;
    }

}

