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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity(), LocationListener {
    private lateinit var web: WebView
    private lateinit var locationManager: LocationManager
    private val requestCode = 41
    private var tracking = false
    private var pageReady = false
    private var latestLocation: Location? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        web = WebView(this)
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                pageReady = true
                latestLocation?.let { showLocation(it) }
            }
        }
        setContentView(web)
        web.loadDataWithBaseURL("https://localhost/", page, "text/html", "UTF-8", null)
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager

        if (hasLocationPermission()) {
            startLocation()
        } else {
            requestPermissions(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ),
                requestCode
            )
        }
    }

    private fun hasLocationPermission(): Boolean {
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    private fun startLocation() {
        if (tracking || !hasLocationPermission()) return
        try {
            var activeProvider = false
            val providers = listOf(
                LocationManager.GPS_PROVIDER,
                LocationManager.NETWORK_PROVIDER
            )
            for (provider in providers) {
                if (locationManager.isProviderEnabled(provider)) {
                    locationManager.requestLocationUpdates(provider, 3000L, 1f, this)
                    activeProvider = true
                    locationManager.getLastKnownLocation(provider)?.let { candidate ->
                        val previous = latestLocation
                        if (previous == null || candidate.time > previous.time ||
                            (candidate.accuracy < previous.accuracy && candidate.time >= previous.time - 30000L)
                        ) {
                            latestLocation = candidate
                            if (pageReady) showLocation(candidate)
                        }
                    }
                }
            }
            tracking = activeProvider
            if (!activeProvider) {
                setStatus("مکان‌یاب گوشی خاموش است؛ GPS یا Location را روشن کنید")
            } else if (latestLocation == null) {
                setStatus("در حال دریافت موقعیت دقیق گوشی…")
            }
        } catch (_: SecurityException) {
            setStatus("دسترسی مکان‌یابی داده نشده است")
        }
    }

    private fun showLocation(location: Location) {
        latestLocation = location
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(location.time))
        val js = "setPhoneLocation(${location.latitude},${location.longitude},${location.accuracy},'$time')"
        runOnUiThread {
            if (::web.isInitialized && pageReady) web.evaluateJavascript(js, null)
        }
    }

    private fun setStatus(message: String) {
        runOnUiThread {
            if (::web.isInitialized && pageReady) {
                val safe = message.replace("\\", "\\\\").replace("'", "\\'")
                web.evaluateJavascript("setStatus('$safe')", null)
            }
        }
    }

    override fun onLocationChanged(location: Location) {
        showLocation(location)
    }

    @Deprecated("Deprecated in Android API")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit

    override fun onProviderEnabled(provider: String) {
        if (hasLocationPermission()) startLocation()
    }

    override fun onProviderDisabled(provider: String) {
        if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) &&
            !locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        ) {
            setStatus("مکان‌یاب گوشی خاموش است")
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == this.requestCode && hasLocationPermission()) {
            startLocation()
        } else if (requestCode == this.requestCode) {
            Toast.makeText(this, "برای نمایش موقعیت گوشی باید اجازه مکان‌یابی بدهید", Toast.LENGTH_LONG).show()
            setStatus("برای استفاده از نقشه، اجازه مکان‌یابی را فعال کنید")
        }
    }

    override fun onResume() {
        super.onResume()
        if (::locationManager.isInitialized && hasLocationPermission()) startLocation()
    }

    override fun onPause() {
        stopLocation()
        super.onPause()
    }

    private fun stopLocation() {
        if (tracking && ::locationManager.isInitialized) {
            locationManager.removeUpdates(this)
            tracking = false
        }
    }

    override fun onDestroy() {
        stopLocation()
        if (::web.isInitialized) web.destroy()
        super.onDestroy()
    }

    private val page = """
        <!doctype html>
        <html lang="fa" dir="rtl">
        <head>
          <meta charset="utf-8">
          <meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1">
          <title>loosi&amp;pashmak&amp;kooki</title>
          <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css">
          <style>
            html,body,#map{height:100%;width:100%;margin:0;font-family:sans-serif}
            .panel{position:absolute;z-index:1000;top:12px;right:12px;left:12px;background:#123b2b;color:white;padding:14px;border-radius:16px;box-shadow:0 2px 12px #3336}
            .title{font-size:16px;font-weight:bold}.status{display:block;margin-top:8px;color:#e1f0e5;font-size:13px;line-height:1.7}
            .meta{display:block;color:#b8d5c2;font-size:11px;margin-top:3px}
            .locate{position:absolute;z-index:1000;bottom:28px;right:16px;border:0;border-radius:13px;background:#d8b45a;color:#183526;padding:13px 17px;font-size:14px;font-weight:bold;box-shadow:0 2px 8px #3335}
            .leaflet-control-attribution{font-size:9px}
          </style>
        </head>
        <body>
          <div id="map"></div>
          <div class="panel">
            <div class="title">🐤 loosi&amp;pashmak&amp;kooki</div>
            <span class="status" id="status">در انتظار دریافت موقعیت گوشی…</span>
            <span class="meta" id="meta">این نسخه موقعیت گوشی را نشان می‌دهد، نه موقعیت پرنده را.</span>
          </div>
          <button class="locate" onclick="centerOnPhone()">موقعیت من</button>
          <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
          <script>
            const map=L.map('map').setView([32.5,53],5);
            L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png',{
              maxZoom:19,attribution:'© OpenStreetMap'
            }).addTo(map);
            let marker=null,circle=null,lastPoint=null;
            function setStatus(message){document.getElementById('status').textContent=message;}
            function setPhoneLocation(lat,lon,accuracy,time){
              lastPoint=[lat,lon];
              if(marker)map.removeLayer(marker);
              if(circle)map.removeLayer(circle);
              marker=L.marker(lastPoint).addTo(map).bindPopup('موقعیت فعلی گوشی');
              circle=L.circle(lastPoint,{radius:accuracy,color:'#1d8a55',fillOpacity:.12}).addTo(map);
              map.setView(lastPoint,16);
              setStatus('موقعیت دریافت شد | دقت تقریبی: '+Math.round(accuracy)+' متر');
              document.getElementById('meta').textContent='آخرین به‌روزرسانی: '+time+' — این نسخه هنوز ردیاب پرنده نیست.';
            }
            function centerOnPhone(){
              if(lastPoint){map.setView(lastPoint,17);if(marker)marker.openPopup();}
              else setStatus('هنوز موقعیتی دریافت نشده؛ GPS گوشی را روشن کنید.');
            }
          </script>
        </body>
        </html>
    """.trimIndent()
}
