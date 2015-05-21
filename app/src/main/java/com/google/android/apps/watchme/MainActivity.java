package com.google.android.apps.watchme;

import android.accounts.AccountManager;
import android.app.Activity;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.IntentSender;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import com.google.android.apps.watchme.util.EventData;
import com.google.android.apps.watchme.util.YouTubeEventsApi;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.Scopes;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.plus.Plus;
import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.ExponentialBackOff;
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.YouTubeScopes;

import java.io.IOException;
import java.util.Arrays;
import java.util.Date;
import java.util.List;


public class MainActivity extends Activity implements View.OnClickListener, GoogleApiClient.OnConnectionFailedListener, GoogleApiClient.ConnectionCallbacks {

    private static final String TAG = MainActivity.class.getName();
    public static final String ACCOUNT_KEY = "accountName";
    public static final String APP_NAME = "WatchMe";

    private static final int REQUEST_GOOGLE_PLAY_SERVICES = 0;
    private static final int REQUEST_GMS_ERROR_DIALOG = 1;
    private static final int REQUEST_ACCOUNT_PICKER = 2;
    private static final int REQUEST_AUTHORIZATION = 3;
    private static final int REQUEST_STREAMER = 4;

    final HttpTransport transport = AndroidHttp.newCompatibleTransport();
    final JsonFactory jsonFactory = new GsonFactory();

    GoogleAccountCredential credential;
    private String mChosenAccountName;
    private ProgressDialog progressDialog;
    private GoogleApiClient mGoogleApiClient;

    private String broadcastId;

    public static final String[] SCOPES = {Scopes.PROFILE, YouTubeScopes.YOUTUBE};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        findViewById(R.id.startStreaming).setOnClickListener(this);
        findViewById(R.id.endStreaming).setOnClickListener(this);

        progressDialog = new ProgressDialog(this);
        progressDialog.setCancelable(false);

        initGoogleConnectParameters(savedInstanceState);

        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addApi(Plus.API)
                .addScope(Plus.SCOPE_PLUS_PROFILE)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();

        mGoogleApiClient.connect();
    }

    private void initGoogleConnectParameters(Bundle savedInstanceState) {
        credential = GoogleAccountCredential.usingOAuth2(
                getApplicationContext(), Arrays.asList(SCOPES));
        // set exponential backoff policy
        credential.setBackOff(new ExponentialBackOff());

        if (savedInstanceState != null) {
            mChosenAccountName = savedInstanceState.getString(ACCOUNT_KEY);
        } else {
            loadAccount();
        }

        credential.setSelectedAccountName(mChosenAccountName);
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.startStreaming:
                if (TextUtils.isEmpty(broadcastId) && LiveStreamDatas.getInstance(this).getMediaProjection() == null) {
                    progressDialog.show();
                    createEvent();
                } else {
                    Toast.makeText(this, "You already streaming your screen", Toast.LENGTH_LONG).show();
                }
                break;
            case R.id.endStreaming:
                if (!TextUtils.isEmpty(broadcastId)) {
                    handleEndEvent();
                } else {
                    Toast.makeText(this, "You haven't event for end", Toast.LENGTH_LONG).show();
                }
                break;
        }
    }

    private void handleEndEvent() {
        if (!TextUtils.isEmpty(broadcastId)) {
            new EndEventTask().execute(broadcastId);
        }
        if (LiveStreamDatas.getInstance(MainActivity.this).getVideoStreamingConnection() != null) {
            LiveStreamDatas.getInstance(MainActivity.this).getVideoStreamingConnection().close();
            LiveStreamDatas.getInstance(MainActivity.this).setVideoStreamingConnection(null);
        }
        if (LiveStreamDatas.getInstance(MainActivity.this).getMediaProjection() != null) {
            LiveStreamDatas.getInstance(MainActivity.this).getMediaProjection().stop();
            LiveStreamDatas.getInstance(MainActivity.this).setMediaProjection(null);
        }
        if (LiveStreamDatas.getInstance(MainActivity.this).getStreamerService() != null) {
            LiveStreamDatas.getInstance(MainActivity.this).setStreamerService(null);
            LiveStreamDatas.getInstance(MainActivity.this).setStreamerConnection(null);
        }
    }

    @Override
    public void onDestroy() {
        super.onPause();
        mGoogleApiClient.disconnect();
    }

    @Override
    public void onConnected(Bundle bundle) {
        Toast.makeText(this, "connected to " + Plus.AccountApi.getAccountName(mGoogleApiClient) , Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        if (connectionResult.hasResolution()) {

            Log.e(TAG, String.format( "Connection to Play Services Failed, error: %d, reason: %s",
                            connectionResult.getErrorCode(),
                            connectionResult.toString()));
            try {
                connectionResult.startResolutionForResult(this, 0);
            } catch (IntentSender.SendIntentException e) {
                Log.e(TAG, e.toString(), e);
            }
        }
    }

    public void startStreaming(EventData event) {


        broadcastId = event.getId();

        new StartEventTask().execute(broadcastId);

        Intent intent = new Intent(getApplicationContext(), StreamerActivity.class);
        intent.putExtra(YouTubeEventsApi.RTMP_URL_KEY, event.getIngestionAddress());
        intent.putExtra(YouTubeEventsApi.BROADCAST_ID_KEY, broadcastId);

        startActivityForResult(intent, REQUEST_STREAMER);
    }

    public void createEvent() {
        if (mChosenAccountName == null) {
            return;
        }
        new CreateLiveEventTask().execute();
    }

    private void loadAccount() {
        SharedPreferences sp = PreferenceManager
                .getDefaultSharedPreferences(this);
        mChosenAccountName = sp.getString(ACCOUNT_KEY, null);
        invalidateOptionsMenu();
    }

    private void saveAccount() {
        SharedPreferences sp = PreferenceManager
                .getDefaultSharedPreferences(this);
        sp.edit().putString(ACCOUNT_KEY, mChosenAccountName).apply();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case REQUEST_GMS_ERROR_DIALOG:
                break;
            case REQUEST_GOOGLE_PLAY_SERVICES:
                if (resultCode == Activity.RESULT_OK) {
                    haveGooglePlayServices();
                } else {
                    checkGooglePlayServicesAvailable();
                }
                break;
            case REQUEST_AUTHORIZATION:
                if (resultCode != Activity.RESULT_OK) {
                    chooseAccount();
                }
                break;
            case REQUEST_ACCOUNT_PICKER:
                if (resultCode == Activity.RESULT_OK && data != null
                        && data.getExtras() != null) {
                    String accountName = data.getExtras().getString(
                            AccountManager.KEY_ACCOUNT_NAME);
                    if (accountName != null) {
                        mChosenAccountName = accountName;
                        credential.setSelectedAccountName(accountName);
                        saveAccount();
                    }
                }
                break;
            case REQUEST_STREAMER:
                if (resultCode == Activity.RESULT_OK && data != null && data.getExtras() != null) {
                    String broadcastId = data.getStringExtra(YouTubeEventsApi.BROADCAST_ID_KEY);
                    if (broadcastId != null) {
                        new EndEventTask().execute(broadcastId);
                    }
                }
                break;

        }
    }

    private boolean checkGooglePlayServicesAvailable() {
        final int connectionStatusCode = GooglePlayServicesUtil
                .isGooglePlayServicesAvailable(this);
        if (GooglePlayServicesUtil.isUserRecoverableError(connectionStatusCode)) {
            showGooglePlayServicesAvailabilityErrorDialog(connectionStatusCode);
            return false;
        }
        return true;
    }

    public void showGooglePlayServicesAvailabilityErrorDialog(
            final int connectionStatusCode) {
        runOnUiThread(new Runnable() {
            public void run() {
                Dialog dialog = GooglePlayServicesUtil.getErrorDialog(
                        connectionStatusCode, MainActivity.this,
                        REQUEST_GOOGLE_PLAY_SERVICES);
                dialog.show();
            }
        });
    }

    private void haveGooglePlayServices() {
        // check if there is already an account selected
        if (credential.getSelectedAccountName() == null) {
            // ask user to choose account
            chooseAccount();
        }
    }

    private void chooseAccount() {
        startActivityForResult(credential.newChooseAccountIntent(),
                REQUEST_ACCOUNT_PICKER);
    }

    private class CreateLiveEventTask extends AsyncTask<Void, Void, List<EventData>> {
        @Override
        protected void onPreExecute() {
        }

        @Override
        protected List<EventData> doInBackground(Void... params) {
            YouTube youtube = new YouTube.Builder(transport, jsonFactory, credential).setApplicationName(APP_NAME).build();
            try {
                String date = new Date().toString();
                YouTubeEventsApi.createLiveEvent(youtube, "Event - " + date, "A live streaming event - " + date);
                return YouTubeEventsApi.getLiveEvents(youtube);

            } catch (UserRecoverableAuthIOException e) {
                startActivityForResult(e.getIntent(), REQUEST_AUTHORIZATION);
            } catch (IOException e) {
                Log.e(MainActivity.APP_NAME, "", e);
            }
            return null;
        }

        @Override
        protected void onPostExecute(List<EventData> fetchedEvents) {
            progressDialog.dismiss();
            startStreaming(fetchedEvents.get(fetchedEvents.size() - 1));
        }
    }

    private class StartEventTask extends AsyncTask<String, Void, Void> {

        @Override
        protected void onPreExecute() {
        }

        @Override
        protected Void doInBackground(String... params) {
            YouTube youtube = new YouTube.Builder(transport, jsonFactory, credential).setApplicationName(APP_NAME).build();
            try {
                YouTubeEventsApi.startEvent(youtube, params[0]);
            } catch (UserRecoverableAuthIOException e) {
                startActivityForResult(e.getIntent(), REQUEST_AUTHORIZATION);
            } catch (IOException e) {
                Log.e(MainActivity.APP_NAME, "", e);
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void param) {
        }

    }

    private class EndEventTask extends AsyncTask<String, Void, Void> {

        @Override
        protected void onPreExecute() {
        }

        @Override
        protected Void doInBackground(String... params) {
            YouTube youtube = new YouTube.Builder(transport, jsonFactory, credential).setApplicationName(APP_NAME).build();
            try {
                if (params.length >= 1) {
                    YouTubeEventsApi.endEvent(youtube, params[0]);
                }
            } catch (UserRecoverableAuthIOException e) {
                startActivityForResult(e.getIntent(), REQUEST_AUTHORIZATION);
            } catch (IOException e) {
                Log.e(MainActivity.APP_NAME, "", e);
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void param) {
            broadcastId = "";
            Toast.makeText(MainActivity.this, "event is stoped", Toast.LENGTH_LONG).show();
        }
    }
}
