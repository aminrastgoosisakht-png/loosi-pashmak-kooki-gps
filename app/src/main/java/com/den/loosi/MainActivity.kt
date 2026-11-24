package com.den.loosi
import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast

class MainActivity : Activity(), LocationListener {
 private lateinit var web: WebView
 private lateinit var lm: LocationManager
 private val requestCode = 41
 override fun onCreate(savedInstanceState: Bundle?) {
  super.onCreate(savedInstanceState)
  web = WebView(this)
  web.settings.javaScriptEnabled = true
  web.settings.domStorageEnabled = true
  web.webViewClient = WebViewClient()
  setContentView(web)
  web.loadDataWithBaseURL("https://localhost/", page, "text/html", "UTF-8", null)
  lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
  if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
   requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), requestCode)
  else startLocation()
 }
 private fun startLocation() {
  try {
   val provider = when {
    lm.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
    lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
    else -> null
   }
   if (provider == null) {
    Toast.makeText(this, "لطفاً مکان‌یاب گوشی را روشن کنید", Toast.LENGTH_LONG).show()
    return
   }
   lm.requestLocationUpdates(provider, 5000L, 2f, this)
   lm.getLastKnownLocation(provider)?.let { onLocationChanged(it) }
  } catch (_: SecurityException) {
   Toast.makeText(this, "اجازه دسترسی به موقعیت داده نشد", Toast.LENGTH_LONG).show()
  }
 }
 override fun onLocationChanged(location: Location) {
  val js = "setPhoneLocation(" + location.latitude + "," + location.longitude + "," + location.accuracy + ")"
  runOnUiThread { web.evaluateJavascript(js, null) }
 }
 override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
  super.onRequestPermissionsResult(requestCode, permissions, grantResults)
  if (requestCode == this.requestCode && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) startLocation()
  else Toast.makeText(this, "برای نمایش موقعیت گوشی باید اجازه مکان‌یابی بدهید", Toast.LENGTH_LONG).show()
 }
 override fun onDestroy() {
  if (::lm.isInitialized) lm.removeUpdates(this)
  if (::web.isInitialized) web.destroy()
  super.onDestroy()
 }
 private val page = """
 <!doctype html><html lang="fa"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
 <title>loosi&amp;pashmak&amp;kooki</title>
 <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css">
 <style>html,body,#map{height:100%;margin:0;font-family:sans-serif}.panel{position:absolute;z-index:1000;top:12px;right:12px;left:12px;background:#123b2b;color:white;padding:14px;border-radius:14px;box-shadow:0 2px 10px #5558}.panel small{display:block;margin-top:6px;color:#d8e9dc}</style>
 </head><body><div id="map"></div><div class="panel"><b>🐤 loosi&amp;pashmak&amp;kooki</b><small id="status">در انتظار دریافت موقعیت گوشی…</small></div>
 <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script><script>
 const map=L.map('map').setView([32.5,53],5);
 L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png',{maxZoom:19,attribution:'© OpenStreetMap'}).addTo(map);
 let marker=null,circle=null;
 function setPhoneLocation(lat,lon,accuracy){
  if(marker)map.removeLayer(marker);if(circle)map.removeLayer(circle);
  marker=L.marker([lat,lon]).addTo(map).bindPopup('موقعیت فعلی گوشی').openPopup();
  circle=L.circle([lat,lon],{radius:accuracy,color:'#1d8a55',fillOpacity:.12}).addTo(map);
  map.setView([lat,lon],16);
  document.getElementById('status').textContent='موقعیت گوشی دریافت شد — دقت تقریبی: '+Math.round(accuracy)+' متر';
 }
 </script></body></html>
 """.trimIndent()
}