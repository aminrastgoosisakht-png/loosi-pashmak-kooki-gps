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
          <title>لوسی، پشمک و کوکی</title>
          <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css">
          <style>
            *{box-sizing:border-box}html,body,#map{height:100%;width:100%;margin:0;font-family:sans-serif}
            .menu-button{position:absolute;z-index:1200;top:14px;right:14px;width:48px;height:48px;border:1px solid #d7b64e;border-radius:14px;background:#07532d;color:#fff;box-shadow:0 3px 12px #0005;font-size:27px;line-height:1;display:grid;place-items:center}
            .menu-pop{display:none;position:absolute;z-index:1201;top:70px;right:14px;width:210px;padding:8px;background:#fff;border:1px solid #d7b64e;border-radius:14px;box-shadow:0 5px 20px #0005}
            .menu-pop button{display:block;width:100%;padding:13px 10px;border:0;border-radius:9px;background:#07532d;color:#fff;font-size:14px;font-weight:bold;text-align:right}
            .locate{position:absolute;z-index:1000;bottom:24px;right:16px;width:54px;height:54px;border:1px solid #d7b64e;border-radius:50%;background:#07532d;color:#fff;font-size:25px;box-shadow:0 2px 8px #3335}
            .bird-pin{display:flex;align-items:center;gap:5px;white-space:nowrap;transform:translateY(-4px)}
            .bird-dot{width:30px;height:30px;border:2px solid #e9c75c;border-radius:50%;background:#07532d;display:grid;place-items:center;font-size:17px;box-shadow:0 0 0 3px #fff9,0 2px 7px #0006;position:relative}
            .bird-dot:after{content:'';position:absolute;top:-3px;left:-3px;width:7px;height:7px;border-radius:50%;background:#ff3030;box-shadow:0 0 7px #f00;animation:blink 1s infinite}
            @keyframes blink{0%,45%{opacity:1}46%,100%{opacity:.12}}
            .bird-name{background:#07532d;color:#fff;border:1px solid #e3c15a;border-radius:8px;padding:4px 7px;font-size:11px;font-weight:bold;box-shadow:0 2px 5px #0004}
            .leaflet-control-attribution{font-size:8px}
            .manage{margin-top:9px;background:#d8b45a;color:#183526;border:0;border-radius:9px;padding:8px 12px;font-weight:bold;font-size:12px}
            .shade{display:none;position:absolute;inset:0;z-index:3000;background:#0009;align-items:center;justify-content:center;padding:18px}
            .sheet{width:100%;max-width:420px;max-height:88%;overflow:auto;background:#fff;color:#183526;border-radius:18px;padding:17px;box-shadow:0 8px 30px #0007}
            .sheet h3{margin:0 0 12px;color:#07532d}.sheet input{width:100%;padding:11px;margin:5px 0 9px;border:1px solid #b8c8bd;border-radius:9px;font-size:14px}
            .sheet label{font-size:12px;font-weight:bold}.actions{display:flex;gap:8px}.actions button{flex:1;padding:11px;border:0;border-radius:9px;background:#07532d;color:white;font-weight:bold}.actions .cancel{background:#777}
            .tracker-row{border:1px solid #d5e1d8;border-radius:10px;padding:9px;margin:7px 0;font-size:12px}
          </style>
        </head>
        <body>
          <div id="map"></div>
          <button class="menu-button" aria-label="فهرست خدمات" onclick="toggleMenu()">☰</button>
          <div class="menu-pop" id="menuPop"><button onclick="openManager()">⚙ مدیریت حساب و ردیاب‌ها</button></div>
          <div class="shade" id="manager">
            <div class="sheet">
              <h3>حساب صاحب پرنده و ردیاب‌ها</h3>
              <label>نام صاحب حساب</label><input id="owner" placeholder="نام صاحب پرنده">
              <label>ایمیل یا شناسه تماس (اختیاری در نسخه آزمایشی)</label><input id="contact" placeholder="ایمیل یا شماره تماس">
              <hr>
              <label>نام پرنده</label><input id="birdName" placeholder="مثلاً لوسی">
              <label>کد اختصاصی ردیاب</label><input id="trackerCode" placeholder="مثلاً LV-1001">
              <label>PIN اتصال ردیاب (در این نسخه فقط محلی ذخیره می‌شود)</label><input id="trackerPin" type="password" placeholder="PIN دستگاه">
              <div class="actions"><button onclick="saveTracker()">ثبت ردیاب</button><button class="cancel" onclick="closeManager()">بستن</button></div>
              <h3 style="margin-top:16px">ردیاب‌های ثبت‌شده</h3><div id="trackerList"></div>
              <p style="font-size:10px;color:#666;line-height:1.7">اطلاعات این نسخه فقط روی همین گوشی ذخیره می‌شود. حساب آنلاین، همگام‌سازی بین گوشی‌ها و اتصال سخت‌افزار هنوز فعال نیست.</p>
            </div>
          </div>
          <button class="locate" aria-label="موقعیت من" onclick="centerOnPhone()">◎</button>
          <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
          <script>
            const map=L.map('map',{zoomControl:false}).setView([32.5,53],5);
            L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png',{maxZoom:19,attribution:'© OpenStreetMap'}).addTo(map);
            L.control.zoom({position:'bottomright'}).addTo(map);
            let birdMarkers=[],circle=null,lastPoint=null;
            const birds=[{name:'لوسی',emoji:'🐤',dx:-9},{name:'پشمک',emoji:'🐦',dx:0},{name:'کوکی',emoji:'🐤',dx:9}];
            function setStatus(message){/* وضعیت در نمای نقشه پنهان است */}
            function toggleMenu(){const m=document.getElementById('menuPop');m.style.display=m.style.display==='block'?'none':'block';}
            function setPhoneLocation(lat,lon,accuracy,time){
              lastPoint=[lat,lon];
              birdMarkers.forEach(m=>map.removeLayer(m));birdMarkers=[];
              if(circle)map.removeLayer(circle);
              birds.forEach(b=>{
                const lngOffset=b.dx*0.00001/Math.max(.2,Math.cos(lat*Math.PI/180));
                const icon=L.divIcon({className:'',html:'<div class="bird-pin"><span class="bird-dot">'+b.emoji+'</span><span class="bird-name">'+b.name+'</span></div>',iconSize:[100,38],iconAnchor:[50,19]});
                const m=L.marker([lat,lon+lngOffset],{icon}).addTo(map).bindPopup(b.name+' — موقعیت فعلی گوشی');
                birdMarkers.push(m);
              });
              circle=L.circle(lastPoint,{radius:accuracy,color:'#16854c',fillOpacity:.10,weight:2}).addTo(map);
              map.setView(lastPoint,16);
              setStatus('موقعیت گوشی دریافت شد و فعلاً برای هر سه پرنده ثبت می‌شود | دقت: '+Math.round(accuracy)+' متر');
              /* نمای اصلی فقط نقشه و نشانگر پرنده‌هاست. */
            }
            function centerOnPhone(){
              if(lastPoint){map.setView(lastPoint,17);if(birdMarkers.length)birdMarkers[0].openPopup();}
              else setStatus('هنوز موقعیتی دریافت نشده؛ GPS گوشی را روشن کنید.');
            }
            function getTrackers(){try{return JSON.parse(localStorage.getItem('birdTrackers')||'[]')}catch(e){return []}}
            function openManager(){
              document.getElementById('menuPop').style.display='none';
              document.getElementById('manager').style.display='flex';
              document.getElementById('owner').value=localStorage.getItem('birdOwner')||'';
              document.getElementById('contact').value=localStorage.getItem('birdContact')||'';
              renderTrackers();
            }
            function closeManager(){document.getElementById('manager').style.display='none'}
            function renderTrackers(){
              const list=getTrackers();const box=document.getElementById('trackerList');
              box.innerHTML=list.length?list.map((t,i)=>'<div class="tracker-row"><b>'+escapeHtml(t.name)+'</b> — '+escapeHtml(t.code)+'<br>وضعیت: ثبت‌شده روی این گوشی <button onclick="removeTracker('+i+')" style="float:left">حذف</button></div>').join(''):'<small>هنوز ردیابی ثبت نشده است.</small>';
            }
            function escapeHtml(s){return String(s).replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));}
            function saveTracker(){
              const owner=document.getElementById('owner').value.trim(),contact=document.getElementById('contact').value.trim();
              const name=document.getElementById('birdName').value.trim(),code=document.getElementById('trackerCode').value.trim(),pin=document.getElementById('trackerPin').value.trim();
              if(!owner||!name||!code||!pin){alert('نام صاحب حساب، نام پرنده، کد ردیاب و PIN را وارد کنید.');return;}
              const list=getTrackers();if(list.some(t=>t.code.toLowerCase()===code.toLowerCase())){alert('این کد ردیاب قبلاً ثبت شده است.');return;}
              localStorage.setItem('birdOwner',owner);localStorage.setItem('birdContact',contact);
              list.push({name,code,pin});localStorage.setItem('birdTrackers',JSON.stringify(list));
              document.getElementById('birdName').value='';document.getElementById('trackerCode').value='';document.getElementById('trackerPin').value='';
              renderTrackers();alert('ردیاب در این گوشی ثبت شد. اتصال واقعی پس از آماده‌شدن سخت‌افزار و سرور فعال می‌شود.');
            }
            function removeTracker(i){const list=getTrackers();list.splice(i,1);localStorage.setItem('birdTrackers',JSON.stringify(list));renderTrackers();}
          </script>
        </body>
        </html>
    """.trimIndent()
}
