package com.securemap.secureapp.models;

public class PointLocation {

    private Double Latitude;
    private Double Longitude;
    private Float Distance;

    public Double getLatitude() {
        return Latitude;
    }

    public void setLatitude(Double latitude) {
        Latitude = latitude;
    }

    public Double getLongitude() {
        return Longitude;
    }

    public void setLongitude(Double longitude) {
        Longitude = longitude;
    }

    public Float getDistance() {
        return Distance;
    }

    public void setDistance(Float distance) {
        Distance = distance;
    }

    @Override
    public String toString() {
        return "Latitude: " + this.Latitude + ", Longitude: " + this.Longitude + ", Distance: " + this.Distance;
    }
}
