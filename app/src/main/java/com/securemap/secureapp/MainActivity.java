package com.securemap.secureapp;

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
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.annotation.SuppressLint;

import com.google.gson.JsonObject;
import com.mapbox.api.geocoding.v5.models.CarmenFeature;
import com.mapbox.android.core.location.LocationEngine;
import com.mapbox.android.core.location.LocationEngineCallback;
import com.mapbox.android.core.location.LocationEngineProvider;
import com.mapbox.android.core.location.LocationEngineRequest;
import com.mapbox.android.core.location.LocationEngineResult;
import com.mapbox.android.core.permissions.PermissionsListener;
import com.mapbox.android.core.permissions.PermissionsManager;
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

import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;

public class MainActivity extends AppCompatActivity implements OnMapReadyCallback, PermissionsListener {

    private static final int REQUEST_CODE_AUTOCOMPLETE = 1;
    private static final int PLACEPICKER_REQUEST_CODE = 5678;
    private static final long DEFAULT_INTERVAL_IN_MILLISECONDS = 1000L;
    private static final long DEFAULT_MAX_WAIT_TIME = DEFAULT_INTERVAL_IN_MILLISECONDS * 5;
    private MapboxMap mapboxMap;
    private MapView mapView;
    private MarkerView markerView;
    private MarkerViewManager markerViewManager;
    private View customView;
    private String geojsonSourceLayerId = "source-id";
    private String symbolIconId = "marker-icon-id";
    private PermissionsManager permissionsManager;
    private LocalizationPlugin localizationPlugin;
    private LocationEngine locationEngine;
    private LocationChangeListeningActivityLocationCallback callback =
            new LocationChangeListeningActivityLocationCallback(this);

    private static Location currentLocation;

    // OnCreate Activity
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Initialize Mapbox with access_token
        Mapbox.getInstance(this, getString(R.string.access_token));
        setContentView(R.layout.activity_main);
        mapView = findViewById(R.id.mapView);
        mapView.onCreate(savedInstanceState);
        mapView.getMapAsync(this);
    }

    @Override
    public void onMapReady(@NonNull final MapboxMap mapboxMap) {
        this.mapboxMap = mapboxMap;
        mapboxMap.setStyle(new Style.Builder().fromUrl("mapbox://styles/carlosdanielpg/cjzho34aq24ma1cpgrydouhvr"),
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
                            MainActivity.this.getResources(), R.drawable.mapbox_marker_icon_default));
                    enableLocationComponent(style);
                    initSearchFab();
                    setUpSource(style);
                    setupLayer(style);
                }
            });
    }

    private void initSearchFab() {
        findViewById(R.id.fab).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new PlaceAutocomplete.IntentBuilder()
                        .accessToken(Mapbox.getAccessToken())
                        .placeOptions(PlaceOptions.builder()
                                .backgroundColor(Color.parseColor("#EEEEEE"))
                                .proximity(Point.fromLngLat(currentLocation.getLongitude(), currentLocation.getLatitude()))
                                .limit(10)
                                //.hint("Ingresa el destino")
                                //.language("es")
                                //.country("MX")
                                .build(PlaceOptions.MODE_CARDS))
                        .build(MainActivity.this);
                startActivityForResult(intent, REQUEST_CODE_AUTOCOMPLETE);
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

    /**
     * Initialize the Maps SDK's LocationComponent
     */
    @SuppressWarnings( {"MissingPermission"})
    private void enableLocationComponent(@NonNull Style loadedMapStyle) {
        // Check if permissions are enabled and if not request
        if (PermissionsManager.areLocationPermissionsGranted(this)) {

            // Get an instance of the component
            LocationComponent locationComponent = mapboxMap.getLocationComponent();

            // Set the LocationComponent activation options
            LocationComponentActivationOptions locationComponentActivationOptions =
                    LocationComponentActivationOptions.builder(this, loadedMapStyle)
                            .useDefaultLocationEngine(false)
                            .build();

            // Activate with the LocationComponentActivationOptions object
            locationComponent.activateLocationComponent(locationComponentActivationOptions);

            // Enable to make component visible
            locationComponent.setLocationComponentEnabled(true);

            // Set the component's camera mode
            locationComponent.setCameraMode(CameraMode.TRACKING);

            // Set the component's render mode
            locationComponent.setRenderMode(RenderMode.COMPASS);

            initLocationEngine();
        } else {
            permissionsManager = new PermissionsManager(this);
            permissionsManager.requestLocationPermissions(this);
        }
    }
    //endregion

    //region Location Request
    /**
     * Set up the LocationEngine and the parameters for querying the device's location
     */
    @SuppressLint("MissingPermission")
    private void initLocationEngine() {
        locationEngine = LocationEngineProvider.getBestLocationEngine(this);

        LocationEngineRequest request = new LocationEngineRequest.Builder(DEFAULT_INTERVAL_IN_MILLISECONDS)
                .setPriority(LocationEngineRequest.PRIORITY_HIGH_ACCURACY)
                .setMaxWaitTime(DEFAULT_MAX_WAIT_TIME).build();

        locationEngine.requestLocationUpdates(request, callback, getMainLooper());
        locationEngine.getLastLocation(callback);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        permissionsManager.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    @Override
    public void onExplanationNeeded(List<String> permissionsToExplain) {

        Toast.makeText(this, R.string.user_location_permission_explanation,
                Toast.LENGTH_LONG).show();
    }

    @Override
    public void onPermissionResult(boolean granted) {
        if (granted) {
            if (mapboxMap.getStyle() != null) {
                enableLocationComponent(mapboxMap.getStyle());
            }
        } else {
            Toast.makeText(this, R.string.user_location_permission_not_granted, Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private static class LocationChangeListeningActivityLocationCallback
            implements LocationEngineCallback<LocationEngineResult> {

        private final WeakReference<MainActivity> activityWeakReference;

        LocationChangeListeningActivityLocationCallback(MainActivity activity) {
            this.activityWeakReference = new WeakReference<>(activity);
        }

        /**
         * The LocationEngineCallback interface's method which fires when the device's location has changed.
         *
         * @param result the LocationEngineResult object which has the last known location within it.
         */
        @Override
        public void onSuccess(LocationEngineResult result) {
            MainActivity activity = activityWeakReference.get();

            if (activity != null) {
                Location location = result.getLastLocation();

                if (location == null) {
                    return;
                } else {
                    currentLocation = location;
                }

                // Create a Toast which displays the new location's coordinates
                Log.d("Tag", String.format(activity.getString(R.string.new_location),
                        String.valueOf(result.getLastLocation().getLatitude()),
                        String.valueOf(result.getLastLocation().getLongitude())));

                // Pass the new location to the Maps SDK's LocationComponent
                if (activity.mapboxMap != null && result.getLastLocation() != null) {
                    activity.mapboxMap.getLocationComponent().forceLocationUpdate(result.getLastLocation());
                }
            }
        }

        /**
         * The LocationEngineCallback interface's method which fires when the device's location can't be captured
         *
         * @param exception the exception message
         */
        @Override
        public void onFailure(@NonNull Exception exception) {
            Log.d("LocationChangeActivity", exception.getLocalizedMessage());
            MainActivity activity = activityWeakReference.get();
            if (activity != null) {
                Toast.makeText(activity, exception.getLocalizedMessage(),
                        Toast.LENGTH_SHORT).show();
            }
        }
    }
    //endregion

    // Event triggered when PlaceAutocomplete ends with result code OK
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == Activity.RESULT_OK && requestCode == REQUEST_CODE_AUTOCOMPLETE) {

            // Retrieve selected location's CarmenFeature
            CarmenFeature selectedCarmenFeature = PlaceAutocomplete.getPlace(data);

            // Create a new FeatureCollection and add a new Feature to it using selectedCarmenFeature above.
            // Then retrieve and update the source designated for showing a selected location's symbol layer icon
            goToPlacePicker(selectedCarmenFeature);

        } else if(resultCode == Activity.RESULT_OK && requestCode == PLACEPICKER_REQUEST_CODE) {
            CarmenFeature selectedCarmenFeature = PlaceAutocomplete.getPlace(data);
            Log.i("tag", "Place: " + selectedCarmenFeature.toJson());
            if (mapboxMap != null) {
                Style style = mapboxMap.getStyle();
                if (style != null) {
                    GeoJsonSource source = style.getSourceAs(geojsonSourceLayerId);
                    if (source != null) {
                        source.setGeoJson(FeatureCollection.fromFeatures(
                                new Feature[] {Feature.fromJson(selectedCarmenFeature.toJson())}));
                    }

                    customView = LayoutInflater.from(MainActivity.this).inflate(
                            R.layout.marker_view_bubble, null);

                    customView.setLayoutParams(new FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT));

                    TextView titleTextView = customView.findViewById(R.id.marker_window_title);
                    titleTextView.setText("Destino");

                    TextView snippetTextView = customView.findViewById(R.id.marker_window_snippet);
                    if(selectedCarmenFeature.address() != null)
                        snippetTextView.setText(selectedCarmenFeature.text() + " #" + selectedCarmenFeature.address());
                    else
                        snippetTextView.setText(selectedCarmenFeature.text());

                    markerView = new MarkerView(new LatLng(((Point) selectedCarmenFeature.geometry()).latitude(), ((Point) selectedCarmenFeature.geometry()).longitude()), customView);
                    markerViewManager.addMarker(markerView);

                    if(style.getLayer("linelayer") != null) {
                        style.removeLayer("linelayer");
                        Log.i("tag", "Removed LineLayer");
                    }

                    if(style.getSource("line-source") != null) {
                        style.removeSource("line-source");
                        Log.i("tag", "Removed LineSource");
                    }
                    List<Point> directRouteCoordinates = new ArrayList<>();
                    directRouteCoordinates.add(Point.fromLngLat(currentLocation.getLongitude(), currentLocation.getLatitude()));
                    directRouteCoordinates.add(Point.fromLngLat(((Point) selectedCarmenFeature.geometry()).longitude(), ((Point) selectedCarmenFeature.geometry()).latitude()));
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
                            .include(new LatLng(((Point) selectedCarmenFeature.geometry()).latitude(), ((Point) selectedCarmenFeature.geometry()).longitude()))
                            .include(new LatLng(currentLocation.getLatitude(), currentLocation.getLongitude()))
                            .build();
                    mapboxMap.animateCamera(CameraUpdateFactory.newLatLngBounds(latLngBounds, 100));
                    // Move map camera to the selected location
                   /* mapboxMap.animateCamera(CameraUpdateFactory.newCameraPosition(
                            new CameraPosition.Builder()
                                    .target(new LatLng(((Point) selectedCarmenFeature.geometry()).latitude(),
                                            ((Point) selectedCarmenFeature.geometry()).longitude()))
                                    .zoom(14)
                                    .build()), 4000);*/

                }
            }
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
// Prevent leaks
        if (locationEngine != null) {
            locationEngine.removeLocationUpdates(callback);
        }
        mapView.onDestroy();
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        mapView.onLowMemory();
    }
}
