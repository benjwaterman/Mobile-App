package uk.ac.lincoln.bwaterman.assignmentapp;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.ContextMenu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.GridView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;

public class TwitchStreams extends Activity {

    ArrayList<String> channelNameList = new ArrayList<>();
    ArrayList<String> channelViewersList = new ArrayList<>();
    ArrayList<String> channelSnapshotList = new ArrayList<>();
    ArrayList<String> channelFollowerList = new ArrayList<>();
    ArrayList<String> channelUrlList = new ArrayList<>();
    ArrayList<String> channelLogoUrlList = new ArrayList<>();
    ArrayList<String> textList = new ArrayList<>();
    String gameName;
    String gameViewers;
    DatabaseHelper databaseHelper;
    FileHelper fileHelper;
    boolean isConnected;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_twitch_streams);
        databaseHelper = new DatabaseHelper(this);
        fileHelper = new FileHelper(getApplicationContext());

        //Start progress spinner while loading
        ProgressBar progressBar = (ProgressBar) findViewById(R.id.progressStreams);
        progressBar.setIndeterminate(true);
        progressBar.setVisibility(View.VISIBLE);

        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            //must match "gameName" of parameter passed as an extra
            gameName = extras.getString("gameName");
            gameViewers = extras.getString("gameViewers");
        }
        TextView titleText = (TextView) findViewById(R.id.titleTextView);
        titleText.setText(gameName);

        TextView subtitleText = (TextView) findViewById(R.id.streamerSubtitleText);
        subtitleText.setText(gameViewers + " people watching this game");

        //replace blank space with + so the url works
        gameName = gameName.replace(" ", "+");
        new ParseJsonStreams().execute();
    }

    public void findGameStores(View view)
    {
        Intent intent = new Intent(this, StoresMap.class);
        startActivity(intent);
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        if (v.getId() == R.id.channelGridView) {
            AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo)menuInfo;
            menu.setHeaderTitle(channelNameList.get(info.position));
            MenuInflater inflater = getMenuInflater();

            //Check if streamer is in favourites
            Cursor cursor = databaseHelper.getNameMatches(channelNameList.get(info.position));
            //If results are returned then streamer is already in favourites
            if(cursor.getCount() > 0)
            {
                inflater.inflate(R.menu.menu_favourites_context, menu);
            }
            //Not in favourites
            else
            {
                inflater.inflate(R.menu.menu_streamer_context, menu);
            }
        }
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
        switch (item.getItemId()) {
            //Open channel info
            case R.id.view:
                Intent intent = new Intent(TwitchStreams.this, TwitchChannel.class);
                intent.putExtra("channelName", channelNameList.get(info.position));
                intent.putExtra("channelFollowers", channelFollowerList.get(info.position));
                startActivity(intent);
                return true;

            //Open stream
            case R.id.watch:
                String url = channelUrlList.get(info.position);
                //If name is in favourites, add to times viewed
                Cursor cursor = databaseHelper.getNameMatches(channelNameList.get(info.position));
                if(cursor.getCount() > 0) {
                    cursor.moveToPosition(0);
                    databaseHelper.updateTimesViewed(cursor);
                    cursor.close();
                }
                Uri streamPage;
                try {
                    streamPage = Uri.parse(url);
                    intent = new Intent(Intent.ACTION_VIEW, streamPage);
                    startActivity(intent);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                return true;

            //Add to favourites
            case R.id.add:
                try {
                    boolean isInserted = databaseHelper.insertData(channelNameList.get(info.position), channelUrlList.get(info.position), "0", channelLogoUrlList.get(info.position));
                    if(isInserted)
                        Toast.makeText(TwitchStreams.this,"Favourite added!",Toast.LENGTH_SHORT).show();
                    else
                        Toast.makeText(TwitchStreams.this,"An error occurred: favourite not added",Toast.LENGTH_SHORT).show();

                } catch (Exception e) {
                    e.printStackTrace();
                }
                return true;

            case R.id.remove:
                try {
                    int result = databaseHelper.deleteData(channelNameList.get(info.position));
                    if(result > 0) {
                        Toast.makeText(TwitchStreams.this, "Favourite removed!", Toast.LENGTH_SHORT).show();
                        fileHelper.deleteFavouriteImages(channelNameList.get(info.position));
                    }
                    else {
                        Toast.makeText(TwitchStreams.this, "An error occurred: favourite not deleted", Toast.LENGTH_SHORT).show();
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                }
                return true;

            default:
                return super.onContextItemSelected(item);
        }
    }

    // Async method to parse json data about streams
    class ParseJsonStreams extends AsyncTask<String, String, String> {

        // set the url of the web service to call
        String yourServiceUrl = "https://api.twitch.tv/kraken/streams?game=";
        ProgressBar progressBar;

        @Override
        protected void onPreExecute() {
            //Get progress bar
            progressBar = (ProgressBar) findViewById(R.id.progressStreams);
            progressBar.setVisibility(View.VISIBLE);
        }

        @Override
        protected String doInBackground(String... arg0) {

            try {
                // create new instance of the httpConnect class
                HttpConnect jParser = new HttpConnect();

                // get json string from service url
                String json = jParser.getJSONFromUrl(yourServiceUrl + gameName, getApplicationContext());

                //If no data was returned or string is null, set connection to false and return out of function
                if(json == null || json.length() == 0) {
                    isConnected = false;
                    return null;
                }
                isConnected = true;

                //base object, contains everything
                JSONObject jsonObject = new JSONObject(json);
                //array from object with specified name
                JSONArray jsonArray = jsonObject.optJSONArray("streams");

                for (int i = 0; i < jsonArray.length(); i++) {
                    //get json object from array
                    JSONObject subObject = jsonArray.getJSONObject(i);

                    if (subObject != null) {
                        //add each tweet to ArrayList as an item
                        String nameToAdd;
                        String viewersToAdd;
                        String followersToAdd;
                        String snapshotUrlToAdd;
                        String urlToAdd;
                        String logoToAdd;
                        try {
                            nameToAdd = subObject.getJSONObject("channel").getString("name");// + " currently has " + subObject.getString("viewers") + " viewers.";
                            channelNameList.add(nameToAdd);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        try {
                            viewersToAdd = subObject.getString("viewers");
                            channelViewersList.add(viewersToAdd);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        try {
                            followersToAdd = subObject.getJSONObject("channel").getString("followers");
                            channelFollowerList.add(followersToAdd);
                            textList.add("<b>" + channelNameList.get(i) + "</b><br>" + channelViewersList.get(i) + " viewers<br>" + channelFollowerList.get(i) + " followers");
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        try {
                            snapshotUrlToAdd = subObject.getJSONObject("preview").getString("medium");
                            //a thumbnail of the stream
                            channelSnapshotList.add(snapshotUrlToAdd);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        try {
                            urlToAdd = subObject.getJSONObject("channel").getString("url");
                            //aad url of stream
                            channelUrlList.add(urlToAdd);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        try {
                            logoToAdd = subObject.getJSONObject("channel").getString("logo");
                            //add the logo to a list, only used if added to favourites
                            channelLogoUrlList.add(logoToAdd);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(String strFromDoInBg) {
            //If not connected, set not connected text to visible and return out of function
            if(!isConnected) {
                TextView text = (TextView)findViewById(R.id.noConnectionText);
                text.setVisibility(View.VISIBLE);
                return;
            }

            //Find grid view stuff
            GridView gridview = (GridView) findViewById(R.id.channelGridView);
            //Set adapter to custom adapter
            gridview.setAdapter(new GridAdapter(TwitchStreams.this, textList, channelSnapshotList, channelNameList, ImageType.STREAM, false));
            //Set it to be long clickable to add to favourites
            gridview.setLongClickable(true);
            //Register for context menu
            registerForContextMenu(gridview);

            //Open the link to the stream
            gridview.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
                    String url = channelUrlList.get(position);
                    //Add to times viewed
                    Cursor cursor = databaseHelper.getNameMatches(channelNameList.get(position));
                    //If already in database
                    if(cursor.getCount() > 0) {
                        cursor.moveToPosition(0);
                        databaseHelper.updateTimesViewed(cursor);
                        cursor.close();
                    }
                    Uri streamPage;
                    try {
                        //streamPage = Uri.parse(url);
                        //Intent intent = new Intent(Intent.ACTION_VIEW, streamPage);
                        Intent intent = new Intent(TwitchStreams.this, TwitchChannel.class);
                        intent.putExtra("channelName", channelNameList.get(position));
                        intent.putExtra("channelFollowers", channelFollowerList.get(position));
                        startActivity(intent);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            });

            //Complete progress spinner after done loading
            progressBar.setVisibility(View.INVISIBLE);
        }
    }

}

