package com.securemap.secureapp.models;

import android.location.Location;

import java.util.ArrayList;

public class RouteResponse {

    private ArrayList<PointLocation> results;

    public ArrayList<PointLocation> getResults() {
        return results;
    }

    public void setResults(ArrayList<PointLocation> results) {
        this.results = results;
    }
}
