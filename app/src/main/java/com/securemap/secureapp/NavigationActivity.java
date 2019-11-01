package com.securemap.secureapp;

import android.app.ProgressDialog;
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
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import com.mapbox.api.directions.v5.DirectionsCriteria;
import com.mapbox.api.directions.v5.models.DirectionsResponse;
import com.mapbox.api.directions.v5.models.DirectionsRoute;
import com.mapbox.api.geocoding.v5.GeocodingCriteria;
import com.mapbox.api.geocoding.v5.MapboxGeocoding;
import com.mapbox.api.geocoding.v5.models.CarmenFeature;
import com.mapbox.api.geocoding.v5.models.GeocodingResponse;
import com.mapbox.api.matching.v5.MapboxMapMatching;
import com.mapbox.api.matching.v5.models.MapMatchingResponse;
import com.mapbox.core.exceptions.ServicesException;
import com.mapbox.geojson.Feature;
import com.mapbox.geojson.FeatureCollection;
import com.mapbox.geojson.LineString;
import com.mapbox.geojson.Point;
import com.mapbox.mapboxsdk.Mapbox;
import com.mapbox.mapboxsdk.geometry.LatLng;
import com.mapbox.mapboxsdk.geometry.LatLngBounds;
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
import com.mapbox.services.android.navigation.ui.v5.NavigationLauncher;
import com.mapbox.services.android.navigation.ui.v5.NavigationLauncherOptions;
import com.mapbox.services.android.navigation.ui.v5.route.NavigationMapRoute;
import com.mapbox.services.android.navigation.v5.navigation.NavigationRoute;
import com.securemap.secureapp.models.LocationID;
import com.securemap.secureapp.models.PointLocation;
import com.securemap.secureapp.models.RouteResponse;
import com.securemap.secureapp.utilities.PointService;
import com.securemap.secureapp.utilities.RouteService;

import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.iconImage;
import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.iconOffset;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;

public class NavigationActivity extends AppCompatActivity implements OnMapReadyCallback {
    private static final int REQUEST_CODE_AUTOCOMPLETE_ORIGIN = 1;
    private static final int REQUEST_CODE_AUTOCOMPLETE_DESTINY = 2;
    private static final int PLACEPICKER_ORIGIN_REQUEST_CODE = 5678;
    private static final int PLACEPICKER_DESTINY_REQUEST_CODE = 5679;
    private MapboxMap mapboxMap;
    private MapView mapView;
    private MarkerView markerViewOrigin = null;
    private MarkerView markerViewDestiny = null;
    private MarkerViewManager markerViewManager;
    private View customView;
    private Button searchButton;
    private String geojsonSourceLayerId = "source-id";
    private String geojsonSourceOriginId = "origin-id";
    private String symbolIconId = "marker-icon-id";
    private LocalizationPlugin localizationPlugin;
    private Retrofit retrofit;
    private final String BASE_URL = "https://stormy-plateau-08743.herokuapp.com/api/";
    private ProgressDialog progressDialog;
    private DirectionsRoute currentRoute;
    private NavigationMapRoute navigationMapRoute;
    private boolean routeFound = false;

    private static final Location origin = new Location("");
    private static final Location destiny = new Location("");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        routeFound = false;
        //getSupportActionBar().setBackgroundDrawable(new ColorDrawable(ContextCompat.getColor(this, R.color.translucent)));
        // Initialize Mapbox with access_token
        Mapbox.getInstance(this, getString(R.string.access_token));
        setContentView(R.layout.activity_navigation_route);
        Intent intent = getIntent();
        origin.setLatitude(intent.getDoubleExtra("origin_latitude", 20.654362));
        origin.setLongitude(intent.getDoubleExtra("origin_longitude", -103.326484));
        destiny.setLatitude(intent.getDoubleExtra("destiny_latitude", 20.654362));
        destiny.setLongitude(intent.getDoubleExtra("destiny_longitude", -103.326484));
        try{
            ActionBar actionBar = getSupportActionBar();
            actionBar.setDisplayShowTitleEnabled(false);
            actionBar.setDisplayHomeAsUpEnabled(true);
        } catch(NullPointerException exception) {
            Log.e("Error", exception.getMessage());
        }

        mapView = findViewById(R.id.mapView);
        mapView.onCreate(savedInstanceState);
        mapView.getMapAsync(this);

        retrofit = new Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        searchButton = findViewById(R.id.search_button);
        searchButton.setOnClickListener((View view) -> {
            if(!routeFound) {
                //fetchRoute();
                progressDialog = new ProgressDialog(NavigationActivity.this);
                progressDialog.setMessage("Please wait...");
                progressDialog.setTitle("Progress Dialog");
                //To show the dialog
                progressDialog.show();
                searchPoints(origin, destiny);
            }
            else {
                startNavigation();
            }
        });
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
        if (resultCode == Activity.RESULT_OK &&
                (requestCode == REQUEST_CODE_AUTOCOMPLETE_ORIGIN || requestCode == REQUEST_CODE_AUTOCOMPLETE_DESTINY)
        ) {
            // Retrieve selected location's CarmenFeature
            CarmenFeature selectedCarmenFeature = PlaceAutocomplete.getPlace(data);

            // Create a new FeatureCollection and add a new Feature to it using selectedCarmenFeature above.
            // Then retrieve and update the source designated for showing a selected location's symbol layer icon
            goToPlacePicker(selectedCarmenFeature, requestCode == REQUEST_CODE_AUTOCOMPLETE_ORIGIN);

        } else if(resultCode == Activity.RESULT_OK && requestCode == PLACEPICKER_ORIGIN_REQUEST_CODE) {
            CarmenFeature selectedCarmenFeature = PlaceAutocomplete.getPlace(data);
            origin.setLongitude(selectedCarmenFeature.center().longitude());
            origin.setLatitude(selectedCarmenFeature.center().latitude());
            putMarkers();
        } else if(resultCode == Activity.RESULT_OK && requestCode == PLACEPICKER_DESTINY_REQUEST_CODE) {
            CarmenFeature selectedCarmenFeature = PlaceAutocomplete.getPlace(data);
            destiny.setLongitude(selectedCarmenFeature.center().longitude());
            destiny.setLatitude(selectedCarmenFeature.center().latitude());
            putMarkers();
        }
    }

    private void goToPlacePicker(CarmenFeature carmenFeature, boolean is_origin) {
        startActivityForResult(
                new PlacePicker.IntentBuilder()
                        .accessToken(getString(R.string.access_token))
                        .placeOptions(PlacePickerOptions.builder()
                                .statingCameraPosition(new CameraPosition.Builder()
                                        .target(new LatLng(((Point) carmenFeature.geometry()).latitude(),
                                                ((Point) carmenFeature.geometry()).longitude())).zoom(16).build())
                                .build())
                        .build(this), (is_origin) ? PLACEPICKER_ORIGIN_REQUEST_CODE : PLACEPICKER_DESTINY_REQUEST_CODE);
    }

    private void reverseGeocoding(final Location location, boolean is_origin) {
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
                            if(feature.address() != null)
                                titleTextView.setText(feature.text() + " #" + feature.address());
                            else
                                titleTextView.setText(feature.text());

                            ImageView icon = customView.findViewById(R.id.marker_icon);
                            icon.setOnClickListener((View view) -> {
                                routeFound = false;
                                searchButton.setText("Buscar ruta");
                                Intent intent = new PlaceAutocomplete.IntentBuilder()
                                        .accessToken(Mapbox.getAccessToken())
                                        .placeOptions(PlaceOptions.builder()
                                                .backgroundColor(Color.parseColor("#EEEEEE"))
                                                .proximity(Point.fromLngLat((is_origin) ? origin.getLongitude() : destiny.getLongitude(),
                                                        (is_origin) ? origin.getLatitude(): destiny.getLatitude()))
                                                .limit(10)
                                                .build(PlaceOptions.MODE_CARDS))
                                        .build(NavigationActivity.this);
                                startActivityForResult(intent, (is_origin) ? REQUEST_CODE_AUTOCOMPLETE_ORIGIN : REQUEST_CODE_AUTOCOMPLETE_DESTINY);
                            });

                            if(is_origin) {
                                if(markerViewOrigin != null) {
                                    markerViewManager.removeMarker(markerViewOrigin);
                                }
                                markerViewOrigin = new MarkerView(new LatLng(((Point) feature.geometry()).latitude(), ((Point) feature.geometry()).longitude()), customView);
                                markerViewManager.addMarker(markerViewOrigin);
                            } else {
                                if(markerViewDestiny != null) {
                                    markerViewManager.removeMarker(markerViewDestiny);
                                }
                                markerViewDestiny = new MarkerView(new LatLng(((Point) feature.geometry()).latitude(), ((Point) feature.geometry()).longitude()), customView);
                                markerViewManager.addMarker(markerViewDestiny);
                            }
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

    private void searchPoints(Location locationOrigin, Location locationDestiny) {
        PointService service = retrofit.create(PointService.class);
        Call<LocationID> locationIDCallOrigin = service.getLocation(locationOrigin.getLatitude(), locationOrigin.getLongitude());
        Call<LocationID> locationIDCallDestiny = service.getLocation(locationDestiny.getLatitude(), locationDestiny.getLongitude());

        locationIDCallOrigin.enqueue(new Callback<LocationID>() {
            @Override
            public void onResponse(Call<LocationID> call, Response<LocationID> response) {
                if(response.isSuccessful()) {
                    LocationID locationIDOrigin = response.body();
                    locationIDCallDestiny.enqueue(new Callback<LocationID>() {
                        @Override
                        public void onResponse(Call<LocationID> call, Response<LocationID> response) {
                            if(response.isSuccessful()) {
                                LocationID locationIDDestiny = response.body();
                                onFoundPoints(locationIDOrigin, locationIDDestiny, locationOrigin, locationDestiny);
                            } else {
                                routeFound = true;
                                searchButton.setText("Iniciar ruta");
                                progressDialog.dismiss();
                                Log.i("tag", "Error in response");
                            }

                        }

                        @Override
                        public void onFailure(Call<LocationID> call, Throwable t) {
                            Log.i("tag", "Failure");
                        }
                    });
                } else {
                    Log.i("tag", "Error in response");
                }
            }

            @Override
            public void onFailure(Call<LocationID> call, Throwable t) {
                Log.i("tag", "Failure");
            }
        });
    }

    private void onFoundPoints(LocationID locationOrigin, LocationID locationDestiny, Location originLocation, Location destinyLocation) {
        RouteService service = retrofit.create(RouteService.class);
        Call<RouteResponse> routeResponseCall = service.getRoute(locationOrigin.getID(), locationDestiny.getID());

        routeResponseCall.enqueue(new Callback<RouteResponse>() {
            @Override
            public void onResponse(Call<RouteResponse> call, Response<RouteResponse> response) {
                if(response.isSuccessful()) {
                    RouteResponse routeResponse = response.body();
                    ArrayList<PointLocation> pointLocations = routeResponse.getResults();
                    List<Point> routeCoordinates = new ArrayList<>();
                    for(PointLocation point:pointLocations) {
                        routeCoordinates.add(Point.fromLngLat(point.getLongitude(), point.getLatitude()));
                    }
                    Style style = mapboxMap.getStyle();
                    if (style != null) {
                        if(style.getLayer("linelayer") != null) {
                            style.removeLayer("linelayer");
                            Log.i("tag", "Removed LineLayer");
                        }

                        if(style.getSource("line-source") != null) {
                            style.removeSource("line-source");
                            Log.i("tag", "Removed LineSource");
                        }
                        style.addSource(new GeoJsonSource("line-source",
                                FeatureCollection.fromFeatures(new Feature[] {Feature.fromGeometry(
                                        LineString.fromLngLats(routeCoordinates)
                                )})));
                        style.addLayer(new LineLayer("linelayer", "line-source").withProperties(
                                //PropertyFactory.lineDasharray(new Float[] {0.01f, 2f}),
                                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                                PropertyFactory.lineWidth(5f),
                                PropertyFactory.lineColor(Color.parseColor("#e55e5e"))
                        ));
                        createCustomRoute(routeCoordinates);
                        //progressDialog.dismiss();
                        //getRoute(originLocation, destinyLocation);
                    }
                } else {
                    progressDialog.dismiss();
                    Log.i("tag", "No results");
                }
            }

            @Override
            public void onFailure(Call<RouteResponse> call, Throwable t) {
                Log.i("tag", "Failure");
            }
        });
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem menuItem) {
        switch (menuItem.getItemId()) {
            case android.R.id.home:
                finish();
                break;
        }
        return (super.onOptionsItemSelected(menuItem));
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

    private void startNavigation() {
        boolean simulationRoute = true;
        NavigationLauncherOptions options = NavigationLauncherOptions.builder()
                .directionsRoute(currentRoute)
                .shouldSimulateRoute(simulationRoute)
                .build();

        NavigationLauncher.startNavigation(NavigationActivity.this, options);
    }

    private void createCustomRoute(List<Point> coordinates) {
        MapboxMapMatching.builder()
                .accessToken(getString(R.string.access_token))
                .coordinates(coordinates)
                .waypointIndices(0, coordinates.size() - 1)
                .language("spanish")
                .steps(true)
                .voiceInstructions(true)
                .bannerInstructions(true)
                .profile(DirectionsCriteria.PROFILE_WALKING)
                .build()
                .enqueueCall(new Callback<MapMatchingResponse>() {
                    @Override
                    public void onResponse(Call<MapMatchingResponse> call, Response<MapMatchingResponse> response) {
                        if(response.isSuccessful()) currentRoute = response.body().matchings().get(0).toDirectionRoute();
                        if(navigationMapRoute != null) navigationMapRoute.removeRoute();
                        else navigationMapRoute = new NavigationMapRoute(null, mapView, mapboxMap, R.style.NavigationMapRoute);
                        navigationMapRoute.addRoute(currentRoute);
                        routeFound = true;
                        searchButton.setText("Iniciar ruta");
                        progressDialog.dismiss();
                    }

                    @Override
                    public void onFailure(Call<MapMatchingResponse> call, Throwable t) {
                        progressDialog.dismiss();
                    }
                });
    }

    private void getRoute(Location originLocation, Location destinyLocation) {
        NavigationRoute.builder(this)
                .accessToken(getString(R.string.access_token))
                .origin(Point.fromLngLat(originLocation.getLongitude(), originLocation.getLatitude()))
                .destination(Point.fromLngLat(destinyLocation.getLongitude(), destinyLocation.getLatitude()))
                .build()
                .getRoute(new Callback<DirectionsResponse>() {
                    @Override
                    public void onResponse(Call<DirectionsResponse> call, Response<DirectionsResponse> response) {
                        if(validRouteResponse(response)) {
                            currentRoute = response.body().routes().get(0);
                            if(navigationMapRoute != null) navigationMapRoute.removeRoute();
                            else navigationMapRoute = new NavigationMapRoute(null, mapView, mapboxMap, R.style.NavigationMapRoute);
                            navigationMapRoute.addRoute(currentRoute);
                        }
                    }

                    @Override
                    public void onFailure(Call<DirectionsResponse> call, Throwable t) {

                    }
                });
    }

    private boolean validRouteResponse(Response<DirectionsResponse> response) {
        return response.body() != null && !response.body().routes().isEmpty();
    }


}
