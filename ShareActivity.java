package io.cozy.drive.mobile;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.util.Log;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;

public class ShareActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
//        setContentView(R.layout.activity_share);

        Intent intent = getIntent();
        String action = intent.getAction();
        String type = intent.getType();
        if (Intent.ACTION_SEND.equals(action) && type != null) {
            handleSend(intent);
        } else if (Intent.ACTION_SEND_MULTIPLE.equals(action) && type != null) {
            handleSendMultiple(intent);
        }
    }

    private void handleSend(Intent intent) {
        Uri uri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
        if (uri != null) {
            new UploadFileTask().execute(uri);
        }
    }

    private void handleSendMultiple(Intent intent) {
        ArrayList<Uri> uris = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
        if (uris!= null) {
            for(Uri uri : uris) {
                new UploadFileTask().execute(uri);
            }

        }
    }

    private class UploadFileTask extends AsyncTask<Uri, Integer, Boolean> {

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
        }

        @Override
        protected Boolean doInBackground(Uri... aUris) {

            boolean ret = false;

            final int MAX_BUFFER_SZ = 1 * 1024;


            Uri file_uri = aUris[0];
            String file_path = getFileName(file_uri);

            SharedPreferences sp = getApplicationContext().getSharedPreferences(getApplicationContext().getString(R.string.preference_file_key), Context.MODE_PRIVATE);

            // TODO: decrypt !!
            String upload_url = sp.getString("cozy_url", "unknown") + "/files/io.cozy.files.root-dir?Type=file&Name=" + file_path + "&Tags=file&Executable=false";
            String token = sp.getString("cozy_token", "unknown");
            HttpURLConnection huc = null;
            try {
                int bytes_read, bytes_available, buffer_size, total_bytes;
                byte[] buffer;
                huc = (HttpURLConnection)new URL(upload_url).openConnection();


                // get file sz
                InputStream fis = getContentResolver().openInputStream(file_uri);
                bytes_available = fis.available();
                final int FILE_SZ = bytes_available;

                huc.setRequestProperty("Authorization", token);
                huc.setRequestProperty("Content-Type", getContentResolver().getType(file_uri));
                huc.setRequestProperty("Content-Length", "" + FILE_SZ);
                huc.setRequestProperty("User-Agent", System.getProperty("http.agent"));

                // config request
                huc.setUseCaches(false);
                huc.setDoInput(true);
                huc.setDoOutput(true);
                huc.setRequestMethod("POST");
                huc.setChunkedStreamingMode(MAX_BUFFER_SZ);


                // read input file chunks + publish progress
                OutputStream out = huc.getOutputStream();
                buffer_size = Math.min(bytes_available, MAX_BUFFER_SZ);
                buffer = new byte[buffer_size];
                bytes_read = fis.read(buffer, 0, buffer_size);
                total_bytes = bytes_read;
                while (bytes_read > 0) {
                    out.write(buffer, 0, buffer_size);
                    bytes_available = fis.available();
                    buffer_size = Math.min(bytes_available, MAX_BUFFER_SZ);
                    bytes_read = fis.read(buffer, 0, buffer_size);
                    total_bytes += bytes_read;
                    publishProgress(buffer_size, total_bytes, FILE_SZ);
                }
                out.flush();
                out.close();


                // get server response
                int response_code = huc.getResponseCode();
                String response_message = huc.getResponseMessage();

                // back to JS
                if(response_code / 100 != 2) {
                    // error
                    JSONObject json_error = new JSONObject();
                    json_error.put("code", response_code);
                    json_error.put("source", file_path);
                    json_error.put("target", upload_url);
                    json_error.put("message", response_message);

                    // PluginResult pr = new PluginResult(PluginResult.Status.ERROR, json_error);
                    // mCallback.sendPluginResult(pr);
                }
                else {
                    // ok
                    InputStream is = huc.getInputStream();
                    StringBuilder sb = new StringBuilder();
                    BufferedReader br = new BufferedReader(new InputStreamReader(is));
                    String read;
                    while((read = br.readLine()) != null) {
                        sb.append(read);
                    }
                    br.close();
                    // PluginResult pr = new PluginResult(PluginResult.Status.OK, sb.toString());
                    // mCallback.sendPluginResult(pr);
                    ret = true;
                }
            } catch (final Exception e) {
                e.printStackTrace();
                JSONObject json_error = new JSONObject();
                try {
                    json_error.put("code", -1);
                    json_error.put("source", file_path);
                    json_error.put("target", upload_url);
                    json_error.put("message", e.getLocalizedMessage());
                }
                catch (JSONException ex) {

                }

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        // show error message
                        Toast.makeText(getApplicationContext(), R.string.share_error_title, Toast.LENGTH_LONG);
                    }
                });


            } finally {
                if(huc != null) {
                    huc.disconnect();
                }
            }
            return ret;
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            super.onProgressUpdate(values);
            Log.i("", "Wrote " + values[0] + " bytes (total: " + values[1] + " / " + values[2] + ")" );

            // TODO: send progress
            runOnUiThread(new Runnable() {
                @Override
                public void run() {

                }
            });
        }


        @Override
        protected void onPostExecute(Boolean aResponseCode) {
            super.onPostExecute(aResponseCode);
        }


        private String getFileName(Uri uri) {
            String result = null;
            if (uri.getScheme().equals("content")) {
                Cursor cursor = getContentResolver().query(uri, null, null, null, null);
                try {
                    if (cursor != null && cursor.moveToFirst()) {
                        result = cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME));
                    }
                } finally {
                    cursor.close();
                }
            }
            if (result == null) {
                result = uri.getPath();
                int cut = result.lastIndexOf('/');
                if (cut != -1) {
                    result = result.substring(cut + 1);
                }
            }
            return result;
        }
    }
}
