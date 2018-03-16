package de.fhg.iais.roberta.connection;

import java.io.BufferedInputStream;
//import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.net.URLConnection;
import java.util.HashMap;
import java.util.Map;

import javax.net.ssl.HttpsURLConnection;

import org.apache.commons.io.IOUtils;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * The server communicator runs the server protocol on behalf of the actual robot hardware.
 * This class provides access to push requests, downloads the user program and download system libraries for
 * the upload funtion.
 *
 * @author dpyka
 */
public class ServerCommunicator {

    private String serverpushAddress;
    private String serverdownloadAddress;
    private String serverupdateAddress;

    private String filename = "";

    /**
     * @param serverAddress either the default address taken from the properties file or the custom address entered in the gui.
     */
    public ServerCommunicator(String serverAddress) {
        updateCustomServerAddress(serverAddress);
    }

    /**
     * Update the server address if the user wants to use an own installation of open roberta with a different IP address.
     *
     * @param customServerAddress for example localhost:1999 or 192.168.178.10:1337
     */
    public void updateCustomServerAddress(String customServerAddress) {
        this.serverpushAddress = customServerAddress + "/rest/pushcmd";
        this.serverdownloadAddress = customServerAddress + "/rest/download";
        this.serverupdateAddress = customServerAddress + "/rest/update";
    }

    /**
     * @return the file name of the last binary file downloaded of the server communicator object.
     */
    public String getFilename() {
        return this.filename;
    }

    /**
     * Sends a push request to the open roberta server for registration or keeping the connection alive. This will be hold by the server for approximately 10
     * seconds and then answered.
     *
     * @param requestContent data from the EV3 plus the token and the command send to the server (CMD_REGISTER or CMD_PUSH)
     * @return response from the server
     * @throws IOException if the server is unreachable for whatever reason.
     */
    public JSONObject pushRequest(JSONObject requestContent) throws IOException, JSONException {
        HashMap<String, String> requestProperties = new HashMap<>();
        requestProperties.put("Accept", "application/json");

        URLConnection conn = openURLConnection(this.serverpushAddress, "POST", requestProperties);
        sendServerRequest(requestContent, conn);
        String responseText = getServerResponse(conn);

        return new JSONObject(responseText);
    }

    private URLConnection openURLConnection(String url, String requestMethod, Map<String, String> requestProperties)
        throws MalformedURLException,
        IOException,
        ProtocolException {
        URLConnection conn;
        try {
            conn = getHttpsConnection(url, requestMethod, requestProperties);
            conn.connect();
        } catch ( IOException ioException ) {
            conn = getHttpConnection(url, requestMethod, requestProperties);
            conn.connect();
        }
        return conn;
    }

    private HttpURLConnection getHttpConnection(String urlAddress, String requestMethod, Map<String, String> requestProperties)
        throws MalformedURLException,
        IOException,
        ProtocolException {
        URL url = new URL("http://" + urlAddress);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();

        setURLConnectionProperties(conn, "POST", requestProperties);
        return conn;
    }

    private HttpsURLConnection getHttpsConnection(String urlAddress, String requestMethod, Map<String, String> requestProperties)
        throws MalformedURLException,
        IOException,
        ProtocolException {
        URL url = new URL("https://" + urlAddress);
        HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
        setURLConnectionProperties(conn, requestMethod, requestProperties);
        return conn;
    }

    private void setURLConnectionProperties(HttpURLConnection conn, String requestMethod, Map<String, String> requestProperties) throws ProtocolException {
        conn.setConnectTimeout(5000);
        conn.setDoOutput(true);
        conn.setRequestMethod(requestMethod);

        for ( Map.Entry<String, String> property : requestProperties.entrySet() ) {
            conn.setRequestProperty(property.getKey(), property.getValue());
        }
        conn.setRequestProperty("Accept-Charset", "UTF-8");
        conn.setRequestProperty("Content-Type", "application/json");
    }

    private String getServerResponse(URLConnection conn) throws IOException {
        InputStream responseEntity = new BufferedInputStream(conn.getInputStream());
        String responseText = "";
        if ( responseEntity != null ) {
            responseText = IOUtils.toString(responseEntity, "UTF-8");
        }
        responseEntity.close();
        return responseText;
    }

    private void sendServerRequest(JSONObject requestContent, URLConnection conn) throws IOException, UnsupportedEncodingException {
        OutputStream os = conn.getOutputStream();
        os.write(requestContent.toString().getBytes("UTF-8"));
        os.flush();
        os.close();
    }

    /**
     * Downloads a user program from the server as binary. The http POST is used here.
     *
     * @param requestContent all the content of a standard push request.
     * @return
     * @throws IOException if the server is unreachable or something is wrong with the binary content.
     */
    public byte[] downloadProgram(JSONObject requestContent) throws IOException {
        HashMap<String, String> requestProperties = new HashMap<>();
        requestProperties.put("Accept", "application/octet-stream");

        URLConnection conn = openURLConnection(this.serverdownloadAddress, "POST", requestProperties);
        sendServerRequest(requestContent, conn);

        byte[] binaryfile = getBinaryFileFromResponse(conn);

        return binaryfile;
    }

    /**
     * Basically the same as downloading a user program but without any information about the EV3. It uses http GET(!).
     *
     * @param fwFile name of the file in the url as suffix ( .../rest/update/ev3menu)
     * @return
     * @throws IOException if the server is unreachable or something is wrong with the binary content.
     */
    public byte[] downloadFirmwareFile(String fwFile) throws IOException {

        HashMap<String, String> requestProperties = new HashMap<>();
        requestProperties.put("Accept", "application/octet-stream");

        URLConnection conn = openURLConnection(this.serverupdateAddress + "/" + fwFile, "GET", requestProperties);

        byte[] binaryfile = getBinaryFileFromResponse(conn);

        return binaryfile;
    }

    private byte[] getBinaryFileFromResponse(URLConnection conn) throws IOException {
        InputStream responseEntity = new BufferedInputStream(conn.getInputStream());

        byte[] binaryfile = null;
        if ( responseEntity != null ) {
            this.filename = conn.getHeaderField("Filename");
            binaryfile = IOUtils.toByteArray(responseEntity);
        }
        responseEntity.close();
        return binaryfile;
    }

}
