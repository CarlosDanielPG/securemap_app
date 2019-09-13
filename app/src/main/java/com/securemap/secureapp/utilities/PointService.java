package com.securemap.secureapp.utilities;

import com.securemap.secureapp.models.LocationID;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.http.GET;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface PointService {

    @GET("point")
    Call<LocationID> getLocation(
            @Query("lat") Double latitude,
            @Query("lng") Double longitude);

}
