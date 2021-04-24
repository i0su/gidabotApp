package com.github.gidabotapp.ui;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.Toast;

import com.github.gidabotapp.R;
import com.github.gidabotapp.domain.AppNavPhase;
import com.github.gidabotapp.domain.Floor;
import com.github.gidabotapp.domain.MapPosition;
import com.github.gidabotapp.domain.MultiNavPhase;
import com.github.gidabotapp.domain.Room;
import com.github.gidabotapp.viewmodel.MapViewModel;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Tile;
import com.google.android.gms.maps.model.TileOverlay;
import com.google.android.gms.maps.model.TileOverlayOptions;
import com.google.android.gms.maps.model.TileProvider;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputLayout;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class RouteSelectActivity extends AppCompatActivity implements OnMapReadyCallback {

    private Marker robotMarker;
    private ArrayList<Marker> roomMarkers;

    private MapViewModel viewModel;

    private GoogleMap map;
    private TileOverlay tileOverlay;

//    private Handler mHandler;
//    private Runnable mStatusChecker;
//    private final int MAP_UPDATE_INTERVAL = 1000; // ms

    private Button publishBtn, cancelBtn;
    private TextInputLayout til_non, til_nora, til_floor;
    private AutoCompleteTextView act_non, act_nora, act_floor;
    private FloatingActionButton locateRobotBtn;

    private final int MAX_MAP_ZOOM = 3;

    public RouteSelectActivity() {
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_route_select);

        viewModel = new ViewModelProvider(this, ViewModelProvider.AndroidViewModelFactory.getInstance(this.getApplication())).get(MapViewModel.class);
        viewModel.getToastObserver().observe(this, new Observer<String>() {
            @Override
            public void onChanged(String message) {
                Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
            }
        });
        viewModel.getAlertObserver().observe(this, new Observer<Integer>() {
            @Override
            public void onChanged(Integer stringResId) {
                if(stringResId == R.string.origin_reached_msg){
                    showNextGoalAlert();
                }
                else if (viewModel.getAppNavPhase() != AppNavPhase.WAIT_USER_INPUT){
                    String message = getString(stringResId);
                    showAlert(message);
                }
            }
        });
        viewModel.getPositionObserver().observe(this, new Observer<MapPosition>() {
            @Override
            public void onChanged(MapPosition position) {
                drawRobot(position);
            }
        });
        viewModel.getAllRoomsLD().observe(this, new Observer<List<Room>>() {
            @Override
            public void onChanged(List<Room> rooms) {
                ArrayAdapter<Room> adapterAllRooms = new ArrayAdapter<>(getApplicationContext(), R.layout.support_simple_spinner_dropdown_item, rooms);
//                viewModel.setAllRooms(rooms);
                act_nora.setAdapter(adapterAllRooms);
            }
        });

        publishBtn = findViewById(R.id.publishBtn);
        publishBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                viewModel.publishOrigin();
            }
        });

        cancelBtn = findViewById(R.id.cancelBtn);
        cancelBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                viewModel.publishCancel();
            }
        });

        locateRobotBtn = findViewById(R.id.locateRobotBtn);
        locateRobotBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setCameraOnRobot();
            }
        });

        til_non = findViewById(R.id.til_non);
        act_non = findViewById(R.id.act_non);
        act_non.setThreshold(1);
        act_non.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                // Hide Keyboard
                InputMethodManager in = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                in.hideSoftInputFromWindow(view.getApplicationWindowToken(), 0);

                Room origin = (Room) parent.getItemAtPosition(position);
                viewModel.selectOrigin(origin);
            }
        });

        til_nora = findViewById(R.id.til_nora);
        act_nora = findViewById(R.id.act_nora);
        act_nora.setThreshold(1);
        act_nora.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                // Hide Keyboard
                InputMethodManager in = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                in.hideSoftInputFromWindow(view.getApplicationWindowToken(), 0);

                Room destination = (Room) parent.getItemAtPosition(position);
                viewModel.selectDestination(destination);
            }
        });

        til_floor = findViewById(R.id.til_floor);
        act_floor = findViewById(R.id.act_floor);
        final ArrayAdapter<String> floorAdapter = new ArrayAdapter<>(this, R.layout.support_simple_spinner_dropdown_item, Floor.getFloorList());
        act_floor.setAdapter(floorAdapter);
        act_floor.setText(floorAdapter.getItem(0),false);
        act_floor.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                viewModel.selectFloor(position);
            }
        });


        viewModel.getCurrentFloorRooms().observe(this, new Observer<List<Room>>() {
            @Override
            public void onChanged(List<Room> rooms) {
                ArrayAdapter<Room> adapterFloorRooms = new ArrayAdapter<>(getApplicationContext(), R.layout.support_simple_spinner_dropdown_item, rooms);
                act_non.setAdapter(adapterFloorRooms);
                act_non.setText("");
                viewModel.selectOrigin(null);
                drawNewTiles(rooms);
            }
        });

        viewModel.getNavPhaseObserver().observe(this,new Observer<MultiNavPhase>(){
            @Override
            public void onChanged(MultiNavPhase multiNavPhase) {
                enableButtons(multiNavPhase);
            }

        });

        SupportMapFragment mapFragment =
                (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        assert mapFragment != null;
        mapFragment.getMapAsync(this);
    }

    // TODO
    private void enableButtons(MultiNavPhase multiNavPhase) {
//        // disable spinners and publish button
//        // enable cancel button
//        if (navPhase == NavPhase.CONTINUE_NAVIGATION){
//            spinnerNora.setEnabled(false);
//            spinnerNon.setEnabled(false);
//            publishBtn.setEnabled(false);
//            cancelBtn.setEnabled(true);
//        }
//
//        // disable cancel button
//        // enable spinners and publish button
//        if (navPhase == NavPhase.WAIT_NAVIGATION){
//            spinnerNora.setEnabled(true);
//            spinnerNon.setEnabled(true);
//            publishBtn.setEnabled(true);
//            cancelBtn.setEnabled(false);
//        }
    }

    private void showAlert(String msg) {
         MaterialAlertDialogBuilder dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.alert_title))
                .setMessage(msg)
                .setPositiveButton(R.string.accept_btn, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss(); // publish destination
                    }
                });
        dialog.show();
    }

    private void showNextGoalAlert(){
        String message = String.format(getString(R.string.origin_reached_msg),viewModel.getDestination());
        MaterialAlertDialogBuilder dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.alert_title)
                .setMessage(message)
                .setPositiveButton(R.string.accept_btn, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        viewModel.publishDestination(); // publish destination
                    }
                })
                .setNeutralButton(R.string.cancel_btn, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                });
        dialog.show();
    }

    private void setCameraOnRobot() {
        map.animateCamera(CameraUpdateFactory.newLatLng(robotMarker.getPosition()));
    }

    @Override
    public void onMapReady(GoogleMap map) {
        map.setMapType(GoogleMap.MAP_TYPE_NONE);
        map.setMaxZoomPreference(MAX_MAP_ZOOM);

        LatLng SOUTHWEST_BOUND = new LatLng(-65,-110);
        LatLng NORTHEAST_BOUND = new LatLng(+65,+110);
        LatLngBounds bounds = new LatLngBounds(SOUTHWEST_BOUND,NORTHEAST_BOUND);
        map.setLatLngBoundsForCameraTarget(bounds);

        /*
         * Set custom onClickListener for all markers,
         * so that it only shows Marker's information (if it has any),
         * and hides Google's default buttons, which we don't want.
         */
        final Marker[] lastOpened = {null};
        map.setOnMarkerClickListener(new GoogleMap.OnMarkerClickListener() {
            @Override
            public boolean onMarkerClick(Marker marker) {
                // Check if there is an open info window
                if (lastOpened[0] != null) {
                    // Close the info window
                    lastOpened[0].hideInfoWindow();

                    // Is the marker the same marker that was already open
                    if (lastOpened[0].equals(marker)) {
                        // Nullify the lastOpened object
                        lastOpened[0] = null;
                        // Return so that the info window isn't opened again
                        return true;
                    }
                }

                // Open the info window for the marker
                marker.showInfoWindow();
                // Re-assign the last opened such that we can close it later
                lastOpened[0] = marker;

                // Event was handled by our code, so do not launch default behaviour.
                return true;
            }
        });
        this.map = map;
    }


    private void drawNewTiles(List<Room> rooms){
        if(map != null) {
            final double floor = rooms.get((0)).getFloor();
            generateRoomMarkers(rooms);
            TileProvider tileProvider = new TileProvider() {
                final String FLOOR_MAP_URL_FORMAT =
                        "map_tiles/floor_%.1f/%d/tile_%d_%d.png";
                final int TILE_SIZE_DP = 256;

                @Override
                public Tile getTile(int x, int y, int zoom) {
                    if (!checkTileExists(x, y, zoom)) {
                        return null;
                    }
                    String s = String.format(Locale.US, FLOOR_MAP_URL_FORMAT, floor, zoom, x, y);
                    try {
                        InputStream is = getAssets().open(s);
                        Bitmap bitmap = BitmapFactory.decodeStream(is);
                        ByteArrayOutputStream stream = new ByteArrayOutputStream();
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream);

                        return new Tile(TILE_SIZE_DP, TILE_SIZE_DP, stream.toByteArray());
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    return null;
                }
            };
            // Remove current overlay and robot
            if (tileOverlay != null) {
                tileOverlay.remove();
            }
//            if (robotMarker != null){
//                robotMarker.remove();
//                robotMarker = null;
//            }
            tileOverlay = map.addTileOverlay(new TileOverlayOptions().tileProvider(tileProvider));
        }
    }

    private void drawRobot(MapPosition position){
        LatLng latLng = position.toLatLng();
        int iconId = viewModel.getRobotIconId();

        BitmapDescriptor current_icon = BitmapDescriptorFactory.fromResource(iconId);

        // Get robot name and make first letter uppercase
        String current_name = getResources().getResourceEntryName(iconId).split("_")[0];
        current_name = current_name.substring(0,1).toUpperCase() + current_name.substring(1);

        if(robotMarker == null){
            robotMarker = map.addMarker(new MarkerOptions()
                    .title(current_name)
                    .icon(current_icon)
                    .position(latLng)
                    .zIndex(1.0f)
            );
            robotMarker.showInfoWindow();
            resetCamera();
        }
        else {
            robotMarker.setTitle(current_name);
            robotMarker.setIcon(current_icon);
            robotMarker.setPosition(latLng);
        }
//        resetCamera();
    }

    private void resetCamera() {
        map.animateCamera(CameraUpdateFactory.newCameraPosition(new CameraPosition(robotMarker.getPosition(),0,0,0)));
    }

    private void generateRoomMarkers(List<Room> rooms) {
        // Remove map's current markers markers
        if(roomMarkers != null) {
            for(Marker marker:roomMarkers){
                marker.remove();
            }
        }

        // if instantiated, remove current old markers overwriting with new array
        // else, instantiate marker array
        roomMarkers = new ArrayList<>();

        for(Room room: rooms){
            LatLng latLng = room.getPosition().toLatLng();
            Bitmap textIcon = this.textAsBitmap(room.getName());
            Marker marker = map.addMarker(new MarkerOptions()
                .position(latLng)
//                .title(room.getName())
                .icon(BitmapDescriptorFactory.fromBitmap(textIcon))
                .zIndex(0.9f)
            );
            roomMarkers.add(marker);
        }

    }

    private boolean checkTileExists(int x, int y, int zoom) {
        int minZoom = 0;

        return (zoom >= minZoom && zoom <= MAX_MAP_ZOOM);
    }

    private Bitmap textAsBitmap(String text){
        final float scaleFactor = getApplicationContext().getResources().getDisplayMetrics().density;
        final float TEXT_SIZE = 17f;
        final float render_size = TEXT_SIZE * scaleFactor;

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setTextSize(render_size);
        paint.setColor(getColor(R.color.material_black));
        paint.setTextAlign(Paint.Align.LEFT);

        float baseLine = -paint.ascent();
        int width = (int) (paint.measureText(text) + 1f); // round
        int height = (int) (baseLine + paint.descent() + 0.5f);

        Bitmap image = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(image);
        canvas.drawColor(ContextCompat.getColor(getApplicationContext(),R.color.light_blue));
        canvas.drawText(text, 0, baseLine, paint);
        return image;
    }

    private Bitmap getBitmap(int drawableRes){
        Drawable drawable = ContextCompat.getDrawable(getApplicationContext(),drawableRes);
        Canvas canvas = new Canvas();
        Bitmap bitmap = Bitmap.createBitmap(drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
        canvas.setBitmap(bitmap);
        drawable.setBounds(0, 0, drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight());
        drawable.draw(canvas);

        return bitmap;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        viewModel.getCurrentFloor().removeObservers(this);
        viewModel.getToastObserver().removeObservers(this);
        viewModel.getAlertObserver().removeObservers(this);
        viewModel.getPositionObserver().removeObservers(this);
        viewModel.getCurrentFloorRooms().removeObservers(this);
        viewModel.getNavPhaseObserver().removeObservers(this);
    }

    private static class CoordTileProvider implements TileProvider {

        private static final int TILE_SIZE_DP = 256;

        private final float scaleFactor;

        private final Bitmap borderTile;

        public CoordTileProvider(Context context) {
            /* Scale factor based on density, with a 0.6 multiplier to increase tile generation
             * speed */
            scaleFactor = context.getResources().getDisplayMetrics().density * 0.6f;
            Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            borderPaint.setStyle(Paint.Style.STROKE);
            borderTile = Bitmap.createBitmap((int) (TILE_SIZE_DP * scaleFactor),
                    (int) (TILE_SIZE_DP * scaleFactor), android.graphics.Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(borderTile);
            canvas.drawRect(0, 0, TILE_SIZE_DP * scaleFactor, TILE_SIZE_DP * scaleFactor,
                    borderPaint);
        }

        @Override
        public Tile getTile(int x, int y, int zoom) {
            Bitmap coordTile = drawTileCoords(x, y, zoom);
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            coordTile.compress(Bitmap.CompressFormat.PNG, 0, stream);
            byte[] bitmapData = stream.toByteArray();
            return new Tile((int) (TILE_SIZE_DP * scaleFactor),
                    (int) (TILE_SIZE_DP * scaleFactor), bitmapData);
        }

        private Bitmap drawTileCoords(int x, int y, int zoom) {
            // Synchronize copying the bitmap to avoid a race condition in some devices.
            Bitmap copy = null;
            synchronized (borderTile) {
                copy = borderTile.copy(android.graphics.Bitmap.Config.ARGB_8888, true);
            }
            Canvas canvas = new Canvas(copy);
            String tileCoords = "(" + x + ", " + y + ")";
            String zoomLevel = "zoom = " + zoom;
            /* Paint is not thread safe. */
            Paint mTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            mTextPaint.setTextAlign(Paint.Align.CENTER);
            mTextPaint.setTextSize(18 * scaleFactor);
            canvas.drawText(tileCoords, TILE_SIZE_DP * scaleFactor / 2,
                    TILE_SIZE_DP * scaleFactor / 2, mTextPaint);
            canvas.drawText(zoomLevel, TILE_SIZE_DP * scaleFactor / 2,
                    TILE_SIZE_DP * scaleFactor * 2 / 3, mTextPaint);
            return copy;
        }
    }

}