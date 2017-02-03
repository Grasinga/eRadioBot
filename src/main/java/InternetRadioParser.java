import com.google.gson.*;
import com.google.gson.stream.JsonReader;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;

/**
 * Gets an internet radio station's information.
 */
class InternetRadioParser {

    /**
     * Gets the radio station's name from the url parameter.
     *
     * @param url Radio station's mp3 stream.
     * @return Radio Station's name.
     */
    static String getStationName(String url) {return getInfo(url, "station");}

    /**
     * Gets the radio station's song from the url parameter.
     *
     * @param url Radio station's mp3 stream.
     * @return Current song's title.
     */
    static String getCurrentSong(String url){return getInfo(url, "song");}

    /**
     * Gets the radio station's artist from the url parameter.
     *
     * @param url Radio station's mp3 stream.
     * @return Current song's artist.
     */
    static String getCurrentSongArtist(String url) {return getInfo(url, "artist");}

    /**
     * Gets the radio station's song info from the url parameter.
     *
     * @param url Radio station's mp3 stream.
     * @return Current song's artist and title.
     */
    static String getCurrentSongInfo(String url) {return getInfo(url, "info");}

    /**
     * Gets info from the radio station provided by the url parameter.
     *
     * @param url Radio station's mp3 stream.
     * @param type Type of info wanting to be returned.
     * @return Info based on type parameter.
     * @see #getStationName(String)
     * @see #getCurrentSong(String)
     */
    private static String getInfo(String url, String type){
        String mountPoint = getMountPoint(url);

        URL mount;
        HttpURLConnection request;
        try {
            // Connect to the URL using java's native library
            mount = new URL(getJsonURL(url));
            request = (HttpURLConnection) mount.openConnection();
            request.connect();
        } catch (Exception e) {return "Could not connect to: " + getJsonURL(url);}

        // Convert to a JSON object to print data
        JsonParser jp = new JsonParser(); //from gson
        JsonElement jsonResponse;
        try {
            jsonResponse = jp.parse(new JsonReader(new InputStreamReader((InputStream) request.getContent())));
        } catch (Exception e) {return "Was unable to get JSON data from: " + getJsonURL(url);}

        JsonObject obj = jsonResponse.getAsJsonObject();
        JsonArray sourceArray = obj.getAsJsonObject("icestats").getAsJsonArray("source");

        String stationName = "Unknown";
        String artist = "Unknown";
        String song = "Unknown";
        String fullSongInfo = "Unknown";

        for(JsonElement element : sourceArray){
            if(element.toString().contains(mountPoint)) {
                if(element.getAsJsonObject().get("server_name") != null)
                    stationName = element.getAsJsonObject().get("server_name").getAsString();
                if(element.getAsJsonObject().get("artist") != null)
                    artist = element.getAsJsonObject().get("artist").getAsString();
                if(element.getAsJsonObject().get("title") != null)
                    song = element.getAsJsonObject().get("title").getAsString();
                if(element.getAsJsonObject().get("yp_currently_playing") != null)
                    fullSongInfo = element.getAsJsonObject().get("yp_currently_playing").getAsString();
            }
        }

        if(type.equalsIgnoreCase("station"))
            return stationName;
        if(type.equalsIgnoreCase("song"))
            return song;
        if(type.equalsIgnoreCase("artist"))
            return artist;
        if(type.equalsIgnoreCase("info"))
            return fullSongInfo;


        return "N/A";
    }

    /**
     * Gets the Json file of the radio station.
     *
     * @param url Radio station's mp3 stream.
     * @return Json file from url parameter.
     */
    private static String getJsonURL(String url){
        if(url.length() < 1)
            return "Invalid URL provided; can't get station info!";

        String[] parts = url.split("/");
        if(parts.length > 3)
            return (parts[0] + "//" + parts[2] + "/status-json.xsl");
        else
            return (parts[0] + "/status-json.xsl");
    }

    /**
     * Gets the mounting point of the radio station.
     *
     * @param url Radio station's mp3 stream.
     * @return Gets the mounting point of the url parameter.
     */
    private static String getMountPoint(String url){
        if(url.length() < 1)
            return "Invalid URL provided; can't get mount point!";

        String[] parts = url.split("/");
        if(parts.length > 3)
            return (parts[3]);
        else
            return (parts[1]);
    }
}