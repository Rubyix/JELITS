package com.example.jelits;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.PointF;
import android.location.Location;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.mapbox.geojson.Feature;
import com.mapbox.geojson.FeatureCollection;
import com.mapbox.geojson.LineString;
import com.mapbox.geojson.Point;
import com.mapbox.mapboxsdk.Mapbox;
import com.mapbox.mapboxsdk.camera.CameraPosition;
import com.mapbox.mapboxsdk.geometry.LatLng;
import com.mapbox.mapboxsdk.location.LocationComponent;
import com.mapbox.mapboxsdk.location.LocationComponentActivationOptions;
import com.mapbox.mapboxsdk.location.modes.RenderMode;
import com.mapbox.mapboxsdk.maps.MapView;
import com.mapbox.mapboxsdk.maps.MapboxMap;
import com.mapbox.mapboxsdk.maps.Style;
import com.mapbox.mapboxsdk.style.layers.LineLayer;
import com.mapbox.mapboxsdk.style.layers.PropertyFactory;
import com.mapbox.mapboxsdk.style.layers.SymbolLayer;
import com.mapbox.mapboxsdk.style.sources.GeoJsonSource;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class Maps extends AppCompatActivity {
    // Declare a variable for MapView
    private MapView mapView;
    private MapboxMap mapboxMap;
    private PopupWindow popupWindow;
    private String currentLocation;
    private String selectedLocation;
    private SharedPreferences.OnSharedPreferenceChangeListener sharedPreferenceChangeListener;
    private MediaPlayer mediaPlayer;
    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private static final int REQUEST_LOCATION_PERMISSION = 1;
    private Map<String, String> nodeDescriptionMapping;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Get the API Key by app's BuildConfig
        String key = BuildConfig.MAPTILER_API_KEY;
        String dataApiKey = BuildConfig.DATA_API_KEY;

        // Find other maps in https://cloud.maptiler.com/maps/
        String mapId = "streets-v2";

        String styleUrl = "https://api.maptiler.com/maps/" + mapId + "/style.json?key=" + key;

        // Init MapLibre
        Mapbox.getInstance(this);

        // Init layout view
        setContentView(R.layout.activity_maps);

        // Init the MapView
        mapView = findViewById(R.id.mapView);
        mapView.onCreate(savedInstanceState);

        SharedPreferences sharedPreferences = getSharedPreferences("location_prefs", MODE_PRIVATE);
        currentLocation = sharedPreferences.getString("current_location", "B201"); // Default ke "B201" jika tidak ada lokasi yang disimpan

        Intent intent = getIntent();
        if (intent != null) {
            selectedLocation = intent.getStringExtra("selected_location");
        }

        // Initialize node-to-description mapping
        nodeDescriptionMapping = new HashMap<>();
        nodeDescriptionMapping.put("Tower 2", "Lantai 1 : Lobby \nLantai 2 : Ruang Administrasi \nLantai 3 : Laboratorium Biomedik \nLantai 4 : 401 - 406 \nLantai 5 : 501 - 506 \nLantai 6 : 601 - 606 \nLantai 7 : 701 - 706 \nLantai 8 : 801 - 806 \nLantai 9 : Laboratorium MIOT dan Robotik \nLantai 10 : Mushola");
        nodeDescriptionMapping.put("Node2", "Description for Node2");

        mapView.getMapAsync(map -> {
            mapboxMap = map;
            map.setStyle(styleUrl, style -> {
                // Fetch and add GeoJSON from API
                fetchGeoJsonData(style, mapboxMap, dataApiKey);

                if (currentLocation.equals(selectedLocation)) {
                    showArrivalNotification();
                }

                if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, REQUEST_LOCATION_PERMISSION);
                } else {
                    // Permissions already granted, continue with your logic
                    enableLocationComponent(style);
                }
            });
        });

        sharedPreferenceChangeListener = (sharedPrefs, changedKey) -> {
            if ("current_location".equals(changedKey)) {
                String newLocation = sharedPrefs.getString(changedKey, null);
                if (newLocation != null && !newLocation.equals(currentLocation)) {
                    currentLocation = newLocation;
                    showLocationNotification();
                    // Cek apakah currentLocation sama dengan selectedLocation
                    if (currentLocation.equals(selectedLocation)) {
                        showArrivalNotification();
                    }
                }
            }
        };
        sharedPreferences.registerOnSharedPreferenceChangeListener(sharedPreferenceChangeListener);
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        // Set up location callback
        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                if (locationResult == null) {
                    return;
                }
                for (Location location : locationResult.getLocations()) {
                    // Update UI with location data
                    updateLocationUI(location);
                }
            }
        };
        // Request location updates
        requestLocationUpdates();
    }

    private void requestLocationUpdates() {
        LocationRequest locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000)
                .setWaitForAccurateLocation(false)
                .setMinUpdateIntervalMillis(1000)
                .setMaxUpdateDelayMillis(5000)
                .build();

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, REQUEST_LOCATION_PERMISSION);
            return;
        }
        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
    }

    private void updateLocationUI(Location location) {
        if (mapboxMap == null) {
            // Wait until map is fully loaded before updating the location UI
            return;
        }
        LatLng userLocation = new LatLng(location.getLatitude(), location.getLongitude());
        // Add marker or update the user's location on the map
        mapboxMap.getStyle(style -> {
            GeoJsonSource userLocationSource = style.getSourceAs("user-location-source");
            if (userLocationSource != null) {
                userLocationSource.setGeoJson(Point.fromLngLat(location.getLongitude(), location.getLatitude()));
            }
        });
    }

    private void showLocationNotification() {
        playNotificationSound(R.raw.pindah); // Replace with your actual file name

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Lokasi Terkini")
                .setMessage("Sekarang anda berada di " + currentLocation)
                .setPositiveButton("Lanjut", (dialog, which) -> dialog.dismiss())
                .show();
    }

    private void fetchGeoJsonData(Style style, MapboxMap mapboxMap, String apiKey) {
        String geoJsonUrl = "https://api.maptiler.com/data/f70d1e2c-b560-420a-95de-daacc5c45032/features.json?key=" + apiKey;

        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder()
                .url(geoJsonUrl)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                e.printStackTrace();
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.isSuccessful() && response.body() != null) {
                    String geoJson = response.body().string();

                    runOnUiThread(() -> {
                        FeatureCollection featureCollection = FeatureCollection.fromJson(geoJson);
                        addPropertiesToFeatures(featureCollection);
                        processGeoJsonData(style, mapboxMap, featureCollection);
                    });
                }
            }
        });
    }

    private void addPropertiesToFeatures(FeatureCollection featureCollection) {
        List<Feature> features = featureCollection.features();
        if (features != null) {
            for (Feature feature : features) {
                if (feature.hasProperty("Node")) {
                    String nodeName = feature.getStringProperty("Node");
                    if (imageExists(nodeName)) {
                        feature.addStringProperty("image", nodeName);
                    }
                    if (nodeDescriptionMapping.containsKey(nodeName)) {
                        feature.addStringProperty("description", nodeDescriptionMapping.get(nodeName));
                    }
                }
            }
        }
    }

    private boolean imageExists(String nodeName) {
        try {
            InputStream inputStream = getAssets().open("Images/" + nodeName + ".jpg");
            inputStream.close();
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private void processGeoJsonData(Style style, MapboxMap mapboxMap, FeatureCollection featureCollection) {
        List<Feature> features = featureCollection.features();
        if (features == null) return;

        Feature currentLocationFeature = null;
        Feature selectedLocationFeature = null;

        for (Feature feature : features) {
            if (feature.hasProperty("Node")) {
                if (feature.getStringProperty("Node").equals(selectedLocation)) {
                    selectedLocationFeature = feature;
                }
                if (feature.getStringProperty("Node").equals(currentLocation)) {
                    currentLocationFeature = feature;
                }

                // Load the image for each node
                if (feature.hasProperty("image")) {
                    String imageName = feature.getStringProperty("image");
                    loadImageIntoStyle(style, imageName);
                }
            }
        }

        if (currentLocationFeature != null && currentLocationFeature.geometry() instanceof com.mapbox.geojson.Point) {
            GeoJsonSource currentLocationSource = new GeoJsonSource("current-location-source", currentLocationFeature);
            style.addSource(currentLocationSource);

            style.addImage("current-marker-icon-id", BitmapFactory.decodeResource(getResources(), R.drawable.marker_start));

            SymbolLayer currentLocationLayer = new SymbolLayer("current-location-layer", "current-location-source");
            currentLocationLayer.setProperties(
                    PropertyFactory.iconImage("current-marker-icon-id"),
                    PropertyFactory.iconAllowOverlap(true),
                    PropertyFactory.iconOffset(new Float[]{0f, -8f})
            );
            style.addLayer(currentLocationLayer);

            LatLng currentLatLng = new LatLng(((com.mapbox.geojson.Point) currentLocationFeature.geometry()).latitude(),
                    ((com.mapbox.geojson.Point) currentLocationFeature.geometry()).longitude());
            mapboxMap.setCameraPosition(new CameraPosition.Builder()
                    .target(currentLatLng)
                    .zoom(17.0)
                    .build());
        } else {
            Log.d("GeoJson", "Current location not found in GeoJSON data or not a Point");
        }

        if (selectedLocationFeature != null && selectedLocationFeature.geometry() instanceof com.mapbox.geojson.Point) {
            GeoJsonSource selectedLocationSource = new GeoJsonSource("selected-location-source", selectedLocationFeature);
            style.addSource(selectedLocationSource);

            style.addImage("selected-marker-icon-id", BitmapFactory.decodeResource(getResources(), R.drawable.marker_end));

            SymbolLayer selectedLocationLayer = new SymbolLayer("selected-location-layer", "selected-location-source");
            selectedLocationLayer.setProperties(
                    PropertyFactory.iconImage("selected-marker-icon-id"),
                    PropertyFactory.iconAllowOverlap(true),
                    PropertyFactory.iconOffset(new Float[]{0f, -8f})
            );
            style.addLayer(selectedLocationLayer);

            LatLng selectedLatLng = new LatLng(((com.mapbox.geojson.Point) selectedLocationFeature.geometry()).latitude(),
                    ((com.mapbox.geojson.Point) selectedLocationFeature.geometry()).longitude());
        } else {
            Log.d("GeoJson", "Selected location not found in GeoJSON data or not a Point");
        }

        mapboxMap.addOnMapClickListener(point -> {
            handleMapClick(mapboxMap, point);
            return true;
        });

        Graph graph = parseGeoJsonToGraph(featureCollection);
        AStar aStar = new AStar(graph);

        if (currentLocationFeature != null && currentLocationFeature.geometry() instanceof com.mapbox.geojson.Point &&
                selectedLocationFeature != null && selectedLocationFeature.geometry() instanceof com.mapbox.geojson.Point) {
            LatLng startCoord = new LatLng(((com.mapbox.geojson.Point) currentLocationFeature.geometry()).latitude(),
                    ((com.mapbox.geojson.Point) currentLocationFeature.geometry()).longitude());
            LatLng goalCoord = new LatLng(((com.mapbox.geojson.Point) selectedLocationFeature.geometry()).latitude(),
                    ((com.mapbox.geojson.Point) selectedLocationFeature.geometry()).longitude());

            List<LatLng> path = aStar.findShortestPath(startCoord, goalCoord);
            if (path != null && !path.isEmpty()) {
                drawPath(style, path, features, currentLocationFeature, selectedLocationFeature);
            } else {
                Log.d("AStar", "No path found from " + startCoord + " to " + goalCoord);
            }
        } else {
            Log.d("GeoJson", "Current or selected location feature is null or not a Point");
        }
    }

    private void loadImageIntoStyle(Style style, String imageName) {
        try {
            InputStream inputStream = getAssets().open("Images/" + imageName + ".jpg");
            Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
            style.addImage(imageName, bitmap);
            inputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private Graph parseGeoJsonToGraph(FeatureCollection featureCollection) {
        Graph graph = new Graph();
        List<Feature> features = featureCollection.features();

        if (features != null) {
            for (Feature feature : features) {
                if (feature.geometry() instanceof LineString) {
                    LineString lineString = (LineString) feature.geometry();
                    List<Point> points = lineString.coordinates();

                    for (int i = 0; i < points.size() - 1; i++) {
                        Point point1 = points.get(i);
                        Point point2 = points.get(i + 1);
                        LatLng latLng1 = new LatLng(point1.latitude(), point1.longitude());
                        LatLng latLng2 = new LatLng(point2.latitude(), point2.longitude());
                        graph.addEdge(latLng1, latLng2);
                    }
                }
            }
        }
        Log.d("GeoJson", "Graph created with " + graph.getAllNodes().size() + " nodes and " + graph.getEdges().size() + " edges");
        graph.printNodeConnections(); // Print node connections
        return graph;
    }


    private void drawPath(Style style, List<LatLng> path, List<Feature> allFeatures, Feature currentLocationFeature, Feature selectedLocationFeature) {
        LineString lineString = LineString.fromLngLats(path.stream()
                .map(point -> Point.fromLngLat(point.getLongitude(), point.getLatitude()))
                .toList());
        Feature lineFeature = Feature.fromGeometry(lineString);
        GeoJsonSource lineSource = new GeoJsonSource("path-source", lineFeature);
        style.addSource(lineSource);

        LineLayer lineLayer = new LineLayer("path-layer", "path-source");
        lineLayer.setProperties(
                PropertyFactory.lineColor(Color.parseColor("#FF0000")),
                PropertyFactory.lineWidth(5f)
        );
        style.addLayer(lineLayer);

        // Add path nodes as separate features
        List<Feature> nodeFeatures = new ArrayList<>();
        for (LatLng point : path) {
            for (Feature feature : allFeatures) {
                if (feature.geometry() instanceof Point && feature.hasProperty("Node")) {
                    Point geoPoint = (Point) feature.geometry();
                    if (point.getLatitude() == geoPoint.latitude() && point.getLongitude() == geoPoint.longitude()) {
                        // Check if the feature is not the start or end node
                        if (!feature.equals(currentLocationFeature) && !feature.equals(selectedLocationFeature)) {
                            nodeFeatures.add(feature);
                        }
                        break;
                    }
                }
            }
        }
        FeatureCollection nodeFeatureCollection = FeatureCollection.fromFeatures(nodeFeatures);
        GeoJsonSource nodeSource = new GeoJsonSource("path-nodes-source", nodeFeatureCollection);
        style.addSource(nodeSource);
        style.addImage("node-marker-icon-id", BitmapFactory.decodeResource(getResources(), R.drawable.marker_icon));


        SymbolLayer nodeLayer = new SymbolLayer("path-nodes-layer", "path-nodes-source");
        nodeLayer.setProperties(
                PropertyFactory.iconImage("node-marker-icon-id"),
                PropertyFactory.iconAllowOverlap(true),
                PropertyFactory.iconOffset(new Float[]{0f, -8f})
        );
        style.addLayer(nodeLayer);
    }


    private void handleMapClick(MapboxMap mapboxMap, LatLng clickPoint) {
        // Check for features in the "current-location-layer", "selected-location-layer", and "path-nodes-layer"
        List<Feature> features = mapboxMap.queryRenderedFeatures(mapboxMap.getProjection().toScreenLocation(clickPoint), "current-location-layer", "selected-location-layer", "path-nodes-layer");

        if (!features.isEmpty()) {
            Feature feature = features.get(0);
            if (feature.hasProperty("Node")) {
                String placeName = feature.getStringProperty("Node");

                // Check if there is an image property
                String imageName = null;
                if (feature.hasProperty("image")) {
                    imageName = feature.getStringProperty("image");
                }

                // Check if there is a description property
                String description = feature.hasProperty("description") ? feature.getStringProperty("description") : "";

                // Show custom popup window
                showCustomInfoWindow(mapboxMap, clickPoint, placeName, imageName, description);

                // Log for debugging
                Log.d("MapClick", "Node: " + placeName);
            } else {
                Log.d("MapClick", "Feature does not have 'Node' property"); // Log for debugging
                // Print all properties of the clicked feature
                String properties = feature.properties().toString();
                Log.d("MapClick", "Properties of clicked feature: " + properties);
            }
        } else {
            Log.d("MapClick", "No features found at click point"); // Log for debugging
        }
    }

    private void showCustomInfoWindow(MapboxMap mapboxMap, LatLng point, String placeName, String imageName, String description) {
        // Membuat custom popup window
        View popupView = LayoutInflater.from(this).inflate(R.layout.info_window_layout, null);
        TextView titleTextView = popupView.findViewById(R.id.title);
        ImageView imageView = popupView.findViewById(R.id.image);
        TextView descriptionTextView = popupView.findViewById(R.id.description);

        titleTextView.setText(placeName);
        descriptionTextView.setText(description);

        // Muat gambar dari resources lokal berdasarkan nama gambar
        if (imageName != null && !imageName.isEmpty()) {
            try {
                InputStream inputStream = getAssets().open("Images/" + imageName + ".jpg");
                Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
                imageView.setImageBitmap(bitmap);
                inputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
                imageView.setImageResource(R.drawable.rektorat); // Default image if not found
            }
        } else {
            imageView.setImageResource(R.drawable.rektorat); // Default image
        }

        PopupWindow popupWindow = new PopupWindow(popupView, LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT, true);

        // Mendapatkan posisi layar dari koordinat geografis
        PointF screenPosition = mapboxMap.getProjection().toScreenLocation(point);
        popupView.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);

        // Hitung posisi popup agar berada lebih dekat ke marker
        int offsetX = (int) (screenPosition.x - (popupView.getMeasuredWidth() / 2));
        int offsetY = (int) (screenPosition.y - popupView.getMeasuredHeight()); // Ubah nilai ini untuk menyesuaikan posisi

        // Tampilkan popup window
        popupWindow.showAtLocation(mapView, Gravity.NO_GRAVITY, offsetX, offsetY);

        // Menutup popup window setelah beberapa waktu (misalnya 5 detik)
        popupView.postDelayed(popupWindow::dismiss, 5000); // 5 detik
    }

    private void showArrivalNotification() {
        playNotificationSound(R.raw.sampai); // Replace with your actual file name

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("SAMPAI!!!")
                .setMessage("Anda sudah sampai lokasi tujuan ~(˘▾˘~)")
                .setPositiveButton("Mantap!", (dialog, which) -> dialog.dismiss())
                .show();
    }

    private void enableLocationComponent(Style style) {
        LocationComponent locationComponent = mapboxMap.getLocationComponent();
        locationComponent.activateLocationComponent(
                LocationComponentActivationOptions.builder(this, style).build());
        locationComponent.setLocationComponentEnabled(true);
        locationComponent.setRenderMode(RenderMode.COMPASS);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_LOCATION_PERMISSION);
            return;
        }
        fusedLocationClient.getLastLocation().addOnSuccessListener(this, location -> {
            if (location != null) {
                updateLocationUI(location);
            }
        });
    }


    private void playNotificationSound(int soundResource) {
        if (mediaPlayer != null) {
            mediaPlayer.release();
        }
        mediaPlayer = MediaPlayer.create(this, soundResource);
        mediaPlayer.start();
    }

    @Override
    protected void onStart() {
        super.onStart();
        mapView.onStart();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mapView.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mapView.onPause();
        fusedLocationClient.removeLocationUpdates(locationCallback);
}

    @Override
    protected void onStop() {
        super.onStop();
        mapView.onStop();
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        mapView.onLowMemory();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mapView.onDestroy();
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        mapView.onSaveInstanceState(outState);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_LOCATION_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                requestLocationUpdates();
            }
        }
    }
}