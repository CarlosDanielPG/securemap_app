package com.securemap.secureapp;

import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.ActionBar;
import android.app.Activity;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.location.Location;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.annotation.SuppressLint;

import com.google.gson.JsonObject;
import com.mapbox.api.geocoding.v5.GeocodingCriteria;
import com.mapbox.api.geocoding.v5.MapboxGeocoding;
import com.mapbox.api.geocoding.v5.models.CarmenFeature;
import com.mapbox.android.core.location.LocationEngine;
import com.mapbox.android.core.location.LocationEngineCallback;
import com.mapbox.android.core.location.LocationEngineProvider;
import com.mapbox.android.core.location.LocationEngineRequest;
import com.mapbox.android.core.location.LocationEngineResult;
import com.mapbox.android.core.permissions.PermissionsListener;
import com.mapbox.android.core.permissions.PermissionsManager;
import com.mapbox.api.geocoding.v5.models.GeocodingResponse;
import com.mapbox.core.exceptions.ServicesException;
import com.mapbox.geojson.Feature;
import com.mapbox.geojson.FeatureCollection;
import com.mapbox.geojson.LineString;
import com.mapbox.geojson.Point;
import com.mapbox.mapboxsdk.Mapbox;
import com.mapbox.mapboxsdk.geometry.LatLng;
import com.mapbox.mapboxsdk.geometry.LatLngBounds;
import com.mapbox.mapboxsdk.location.LocationComponent;
import com.mapbox.mapboxsdk.location.LocationComponentActivationOptions;
import com.mapbox.mapboxsdk.location.modes.CameraMode;
import com.mapbox.mapboxsdk.location.modes.RenderMode;
import com.mapbox.mapboxsdk.maps.MapView;
import com.mapbox.mapboxsdk.maps.MapboxMap;
import com.mapbox.mapboxsdk.maps.OnMapReadyCallback;
import com.mapbox.mapboxsdk.maps.Style;
import com.mapbox.mapboxsdk.plugins.places.autocomplete.PlaceAutocomplete;
import com.mapbox.mapboxsdk.plugins.places.autocomplete.model.PlaceOptions;
import com.mapbox.mapboxsdk.plugins.localization.LocalizationPlugin;
import com.mapbox.mapboxsdk.plugins.places.picker.PlacePicker;
import com.mapbox.mapboxsdk.plugins.places.picker.model.PlacePickerOptions;
import com.mapbox.mapboxsdk.plugins.markerview.MarkerView;
import com.mapbox.mapboxsdk.plugins.markerview.MarkerViewManager;
import com.mapbox.mapboxsdk.style.layers.LineLayer;
import com.mapbox.mapboxsdk.style.layers.Property;
import com.mapbox.mapboxsdk.style.layers.PropertyFactory;
import com.mapbox.mapboxsdk.style.layers.SymbolLayer;
import com.mapbox.mapboxsdk.style.sources.GeoJsonSource;
import com.mapbox.mapboxsdk.camera.CameraPosition;
import com.mapbox.mapboxsdk.camera.CameraUpdateFactory;

import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.iconImage;
import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.iconOffset;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;

public class NavigationActivity extends AppCompatActivity implements OnMapReadyCallback {
    private static final int REQUEST_CODE_AUTOCOMPLETE = 1;
    private static final int PLACEPICKER_REQUEST_CODE = 5678;
    private MapboxMap mapboxMap;
    private MapView mapView;
    private MarkerView markerView;
    private MarkerViewManager markerViewManager;
    private View customView;
    private String geojsonSourceLayerId = "source-id";
    private String geojsonSourceOriginId = "origin-id";
    private String symbolIconId = "marker-icon-id";
    private LocalizationPlugin localizationPlugin;

    private static final Location origin = new Location("");
    private static final Location destiny = new Location("");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getSupportActionBar().setBackgroundDrawable(new ColorDrawable(ContextCompat.getColor(this, R.color.translucent)));
        // Initialize Mapbox with access_token
        Mapbox.getInstance(this, getString(R.string.access_token));
        setContentView(R.layout.activity_navigation);
        Intent intent = getIntent();
        origin.setLatitude(intent.getDoubleExtra("origin_latitude", 20.654362));
        origin.setLongitude(intent.getDoubleExtra("origin_longitude", -103.326484));
        destiny.setLatitude(intent.getDoubleExtra("destiny_latitude", 20.654362));
        destiny.setLongitude(intent.getDoubleExtra("destiny_longitude", -103.326484));
        ActionBar actionBar = getSupportActionBar();
        actionBar.hide();
        mapView = findViewById(R.id.mapView);
        mapView.onCreate(savedInstanceState);
        mapView.getMapAsync(this);
    }

    @Override
    public void onMapReady(@NonNull final MapboxMap mapboxMap) {
        this.mapboxMap = mapboxMap;
        mapboxMap.setStyle(Style.MAPBOX_STREETS,
                new Style.OnStyleLoaded() {
                    @Override public void onStyleLoaded(@NonNull Style style) {
                        markerViewManager = new MarkerViewManager(mapView, mapboxMap);
                        localizationPlugin = new LocalizationPlugin(mapView, mapboxMap, style);
                        try {
                            localizationPlugin.matchMapLanguageWithDeviceDefault();

                        } catch (RuntimeException exception) {
                            Log.e("Error", exception.getMessage());
                        }
                        // Add Marker (so confusing bro :c)
                        style.addImage(symbolIconId, BitmapFactory.decodeResource(
                                NavigationActivity.this.getResources(), R.drawable.mapbox_marker_icon_default));
                        setUpSource(style);
                        setupLayer(style);
                        putMarkers();
                    }
                });
    }

    //region Set Up Mapbox SDK
    private void setUpSource(@NonNull Style loadedMapStyle) {
        loadedMapStyle.addSource(new GeoJsonSource(geojsonSourceLayerId));
    }

    private void setupLayer(@NonNull Style loadedMapStyle) {
        loadedMapStyle.addLayer(new SymbolLayer("symbol-layer-id", geojsonSourceLayerId).withProperties(
                iconImage(symbolIconId),
                iconOffset(new Float[] {0f, -8f})
        ));
    }

    private void putMarkers()
    {
        Style style = mapboxMap.getStyle();
        if (style != null) {

            GeoJsonSource source = style.getSourceAs(geojsonSourceLayerId);
            if (source != null) {
                List<Feature> symbolLayerIconFeatureList = new ArrayList<>();
                symbolLayerIconFeatureList.add(Feature.fromGeometry(Point.fromLngLat(origin.getLongitude(), origin.getLatitude())));
                symbolLayerIconFeatureList.add(Feature.fromGeometry(Point.fromLngLat(destiny.getLongitude(), destiny.getLatitude())));
                source.setGeoJson(FeatureCollection.fromFeatures(symbolLayerIconFeatureList));
            }

            if(style.getLayer("linelayer") != null) {
                style.removeLayer("linelayer");
                Log.i("tag", "Removed LineLayer");
            }

            if(style.getSource("line-source") != null) {
                style.removeSource("line-source");
                Log.i("tag", "Removed LineSource");
            }
            List<Point> directRouteCoordinates = new ArrayList<>();
            directRouteCoordinates.add(Point.fromLngLat(origin.getLongitude(), origin.getLatitude()));
            directRouteCoordinates.add(Point.fromLngLat(destiny.getLongitude(), destiny.getLatitude()));
            style.addSource(new GeoJsonSource("line-source",
                    FeatureCollection.fromFeatures(new Feature[] {Feature.fromGeometry(
                            LineString.fromLngLats(directRouteCoordinates)
                    )})));
            style.addLayer(new LineLayer("linelayer", "line-source").withProperties(
                    PropertyFactory.lineDasharray(new Float[] {0.01f, 2f}),
                    PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                    PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                    PropertyFactory.lineWidth(5f),
                    PropertyFactory.lineColor(Color.parseColor("#e55e5e"))
            ));

            LatLngBounds latLngBounds = new LatLngBounds.Builder()
                    .include(new LatLng(origin.getLatitude(), origin.getLongitude()))
                    .include(new LatLng(destiny.getLatitude(), destiny.getLongitude()))
                    .build();
            reverseGeocoding(origin, true);
            reverseGeocoding(destiny, false);
            mapboxMap.animateCamera(CameraUpdateFactory.newLatLngBounds(latLngBounds, 200));

        }
    }

    // Event triggered when PlaceAutocomplete ends with result code OK
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        // Searched direction of destiny
        if (resultCode == Activity.RESULT_OK && requestCode == REQUEST_CODE_AUTOCOMPLETE) {

            // Retrieve selected location's CarmenFeature
            CarmenFeature selectedCarmenFeature = PlaceAutocomplete.getPlace(data);

            // Create a new FeatureCollection and add a new Feature to it using selectedCarmenFeature above.
            // Then retrieve and update the source designated for showing a selected location's symbol layer icon
            goToPlacePicker(selectedCarmenFeature);

        } else if(resultCode == Activity.RESULT_OK && requestCode == PLACEPICKER_REQUEST_CODE) {
        }
    }

    private void goToPlacePicker(CarmenFeature carmenFeature) {
        startActivityForResult(
                new PlacePicker.IntentBuilder()
                        .accessToken(getString(R.string.access_token))
                        .placeOptions(PlacePickerOptions.builder()
                                .statingCameraPosition(new CameraPosition.Builder()
                                        .target(new LatLng(((Point) carmenFeature.geometry()).latitude(),
                                                ((Point) carmenFeature.geometry()).longitude())).zoom(16).build())
                                .build())
                        .build(this), PLACEPICKER_REQUEST_CODE);
    }

    private void reverseGeocoding(final Location location, boolean origin) {
        try {
            MapboxGeocoding reverseGeocoding = MapboxGeocoding.builder()
                    .accessToken(getString(R.string.access_token))
                    .query(Point.fromLngLat(location.getLongitude(), location.getLatitude()))
                    .geocodingTypes(GeocodingCriteria.TYPE_ADDRESS)
                    .build();
            reverseGeocoding.enqueueCall(new Callback<GeocodingResponse>() {
                @Override
                public void onResponse(Call<GeocodingResponse> call, Response<GeocodingResponse> response) {
                    if(response.body() != null) {
                        List<CarmenFeature> results = response.body().features();
                        if(results.size() > 0) {
                            CarmenFeature feature = results.get(0);
                            customView = LayoutInflater.from(NavigationActivity.this).inflate(
                                    R.layout.marker_view_bubble, null);

                            customView.setLayoutParams(new FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT));

                            TextView titleTextView = customView.findViewById(R.id.marker_window_title);
                            if(origin)
                                titleTextView.setText("Origen");
                            else
                                titleTextView.setText("Destino");

                            TextView snippetTextViewOrigin = customView.findViewById(R.id.marker_window_snippet);
                            if(feature.address() != null)
                                snippetTextViewOrigin.setText(feature.text() + " #" + feature.address());
                            else
                                snippetTextViewOrigin.setText(feature.text());

                            markerView = new MarkerView(new LatLng(((Point) feature.geometry()).latitude(), ((Point) feature.geometry()).longitude()), customView);
                            markerViewManager.addMarker(markerView);
                        } else {
                            Log.i("tag", "No hay resultados");
                        }
                    }
                }

                @Override
                public void onFailure(Call<GeocodingResponse> call, Throwable t) {
                    Log.i("Error", "Geocoding Failure: " + t.getMessage());
                }
            });
        } catch(ServicesException serviceException) {
            Log.i("Error", "Geocoding Failure: " + serviceException.toString());
        }
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
    }

    @Override
    protected void onStop() {
        super.onStop();
        mapView.onStop();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        mapView.onSaveInstanceState(outState);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mapView.onDestroy();
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        mapView.onLowMemory();
    }
}
