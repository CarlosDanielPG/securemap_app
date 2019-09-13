package com.securemap.secureapp.utilities;

import com.securemap.secureapp.models.RouteResponse;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface RouteService {

    @GET("dijkstra/route")
    Call<RouteResponse> getRoute(
            @Query("origin") String id_origin,
            @Query("destiny") String id_destiny);

}
