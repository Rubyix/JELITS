package com.example.jelits;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.mapbox.geojson.Feature;
import com.mapbox.geojson.FeatureCollection;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class Mulai extends AppCompatActivity {

    AutoCompleteTextView autoCompleteTextView;

    ArrayAdapter<String> adapterItems;
    private String selectedLocation;  // Variable to store the selected location


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);

        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        setContentView(R.layout.activity_mulai);

        autoCompleteTextView = findViewById(R.id.auto_complete_txt);

        // Fetch and set the locations dynamically from the API
        fetchLocationsFromApi();

        autoCompleteTextView.setOnItemClickListener((adapterView, view, position, id) -> {
            selectedLocation = adapterView.getItemAtPosition(position).toString();
            // You can log or do any additional actions here if needed
            Log.d("Dropdown", "Selected location: " + selectedLocation);
        });


        Button button1 = findViewById(R.id.button1);
        button1.setOnClickListener(v -> {
            // Handle button click
            if (selectedLocation != null && !selectedLocation.isEmpty()) {
                Intent intent = new Intent(Mulai.this, Maps.class);
                intent.putExtra("selected_location", selectedLocation);
                startActivity(intent);
            } else {
                // Show a message or handle the case where no location is selected
                Log.d("Dropdown", "No location selected");
            }
        });

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

    }

    private void fetchLocationsFromApi() {
        String dataApiKey = BuildConfig.DATA_API_KEY;  // Your friend's API key
        String geoJsonUrl = "https://api.maptiler.com/data/f70d1e2c-b560-420a-95de-daacc5c45032/features.json?key=" + dataApiKey;

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
                        List<String> locations = parseGeoJsonLocations(geoJson);
                        adapterItems = new ArrayAdapter<>(Mulai.this, R.layout.list_tujuan, locations);
                        autoCompleteTextView.setAdapter(adapterItems);
                        autoCompleteTextView.setThreshold(1); // Start showing dropdown after 1 character
                    });
                }
            }
        });
    }

    private List<String> parseGeoJsonLocations(String geoJson) {
        List<String> locations = new ArrayList<>();
        List<String> excludedNodes = Arrays.asList("TW1 ke kantin pusat", "Manarul Ilmi ke Rektorat", "Lab fisika ke Kantin Pusat", "Mushola Elektro ke B300 MIOT", "C101 ke Plaza Elektro", "Departemen matematika ke TW1", "TW 2 ke Mushola Elektro", "CCWS ke Manarul Ilmi", "Mushola ke Jembatan AJ-TW 2", "departemen matematika ke lab fisika", "Jembatan TW 2 ke AJ", "Manarul Ilmi"); // Add nodes to be excluded here

        try {
            FeatureCollection featureCollection = FeatureCollection.fromJson(geoJson);
            List<Feature> features = featureCollection.features();
            if (features != null) {
                for (Feature feature : features) {
                    if (feature.hasProperty("Node")) {
                        String locationName = feature.getStringProperty("Node");
                        if (!excludedNodes.contains(locationName)) {
                            locations.add(locationName);
                            Log.d("GeoJSON", "Added location: " + locationName);
                        } else {
                            Log.d("GeoJSON", "Excluded location: " + locationName);
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return locations;
    }
}