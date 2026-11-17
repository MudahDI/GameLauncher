package com.contoh.gamelauncher

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val Bg = Color(0xFF070B14)
private val Panel = Color(0xFF101827)
private val Raised = Color(0xFF1B2A42)
private val PrimaryText = Color(0xFFF4F6FB)
private val Muted = Color(0xFF8998AE)
private val Accent = Color(0xFF8DB8FF)
private val SwitchMint = Color(0xFF80E3C2)
private val CardEdge = Color(0xFF26364D)

data class AppItem(val name: String, val pkg: String, val icon: ImageBitmap, val isGame: Boolean)

fun loadAllApps(ctx: Context): List<AppItem> {
    val pm = ctx.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return pm.queryIntentActivities(intent, 0).map { it.activityInfo.applicationInfo }
        .filter { it.packageName != ctx.packageName }.distinctBy { it.packageName }
        .map { AppItem(it.loadLabel(pm).toString(), it.packageName, it.loadIcon(pm).toBitmap(192,192).asImageBitmap(), it.category == ApplicationInfo.CATEGORY_GAME) }
        .sortedBy { it.name.lowercase() }
}

class Store(ctx: Context) {
    private val sp = ctx.applicationContext.getSharedPreferences("launcher", Context.MODE_PRIVATE)
    private fun set(k:String) = sp.getStringSet(k, emptySet())?.toSet() ?: emptySet()
    private fun list(k:String) = (sp.getString(k,"") ?: "").split(",").filter { it.isNotEmpty() }
    var added by mutableStateOf(set("added"))
    var removed by mutableStateOf(set("removed"))
    var favorites by mutableStateOf(set("favorites"))
    var recent by mutableStateOf(list("recent"))
    private fun save() { sp.edit().putStringSet("added",HashSet(added)).putStringSet("removed",HashSet(removed)).putStringSet("favorites",HashSet(favorites)).putString("recent",recent.joinToString(",")).apply() }
    fun add(p:String) { added=added+p; removed=removed-p; save() }
    fun remove(p:String) { removed=removed+p; added=added-p; favorites=favorites-p; recent=recent-p; save() }
    fun favorite(p:String) { favorites=if(p in favorites) favorites-p else favorites+p; save() }
    fun played(p:String) { recent=(listOf(p)+recent.filter{it!=p}).take(12); save() }
    fun reset() { added=emptySet(); removed=emptySet(); save() }
}

private enum class Page(val title:String,val icon:String) { HOME("Beranda","⌂"), LIBRARY("Koleksi","▦"), SEARCH("Cari","⌕"), SETTINGS("Pengaturan","⚙") }

@Composable private fun NavItem(page:Page, selected:Boolean, wide:Boolean, click:()->Unit) {
    Row(Modifier.fillMaxWidth().height(if(wide)50.dp else 55.dp).clip(RoundedCornerShape(13.dp))
        .background(if(selected) Raised else Color.Transparent).clickable(onClick=click)
        .padding(horizontal=if(wide)14.dp else 3.dp), verticalAlignment=Alignment.CenterVertically,
        horizontalArrangement=if(wide) Arrangement.Start else Arrangement.Center) {
        Text(page.icon,color=if(selected) Accent else Muted,fontSize=21.sp)
        if(wide) { Spacer(Modifier.width(12.dp)); Text(page.title,color=if(selected) PrimaryText else Muted,fontSize=13.sp,fontWeight=if(selected) FontWeight.SemiBold else FontWeight.Normal) }
        else { Spacer(Modifier.width(4.dp)); Text(page.title,color=if(selected) PrimaryText else Muted,fontSize=10.sp) }
    }
}

@Composable private fun Navigation(page:Page, wide:Boolean, choose:(Page)->Unit) {
    if(wide) Column(Modifier.width(205.dp).fillMaxHeight().background(Color(0xF20C1421)).padding(16.dp), verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            Box(Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(Brush.linearGradient(listOf(Accent,SwitchMint))),contentAlignment=Alignment.Center) {
                Text("G",color=Bg,fontSize=20.sp,fontWeight=FontWeight.Black)
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text("GAME",color=Muted,fontSize=9.sp,fontWeight=FontWeight.Bold,letterSpacing=2.sp)
                Text("LAUNCHER",color=PrimaryText,fontSize=19.sp,fontWeight=FontWeight.Black,letterSpacing=0.5.sp)
            }
        }
        Spacer(Modifier.height(25.dp))
        Text("MENU",color=Muted,fontSize=9.sp,fontWeight=FontWeight.Bold,letterSpacing=1.8.sp,modifier=Modifier.padding(start=8.dp,bottom=4.dp))
        Page.values().forEach { NavItem(it,it==page,true){choose(it)} }
        Spacer(Modifier.weight(1f))
        Column {
            Text("GAME LAUNCHER 2.0",color=Muted,fontSize=9.sp)
            Text("Made for play",color=SwitchMint,fontSize=9.sp)
        }
    } else Row(Modifier.fillMaxWidth().background(Color(0xF20C1421)).navigationBarsPadding().padding(8.dp), horizontalArrangement=Arrangement.spacedBy(4.dp)) {
        Page.values().forEach { p -> Box(Modifier.weight(1f)){NavItem(p,p==page,false){choose(p)}} }
    }
}

@Composable private fun Heading(kicker:String,title:String,count:String?=null) {
    Row(Modifier.fillMaxWidth().padding(bottom=18.dp),verticalAlignment=Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(kicker.uppercase(),color=SwitchMint,fontSize=9.sp,fontWeight=FontWeight.Bold,letterSpacing=2.2.sp)
            Text(title,color=PrimaryText,fontSize=29.sp,fontWeight=FontWeight.ExtraBold,letterSpacing=(-0.6).sp)
        }
        if(count!=null) Text(count,color=Muted,fontSize=10.sp,modifier=Modifier.clip(RoundedCornerShape(50)).background(Raised).padding(horizontal=12.dp,vertical=8.dp))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable private fun GameCard(app:AppItem, fav:Boolean, wide:Boolean, play:()->Unit, options:()->Unit) {
    Column(Modifier.width(if(wide)158.dp else 116.dp).combinedClickable(onClick=play,onLongClick=options)) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(15.dp)).background(Panel)) {
            Image(app.icon,app.name,contentScale=ContentScale.Fit,modifier=Modifier.fillMaxSize().padding(7.dp))
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0x22000000),Color(0x00000000),Color(0x99060B13)))))
            Box(Modifier.align(Alignment.BottomStart).padding(9.dp).size(29.dp).clip(RoundedCornerShape(9.dp)).background(Color(0xCC0A1220)),contentAlignment=Alignment.Center) {
                Text("▶",color=SwitchMint,fontSize=12.sp,fontWeight=FontWeight.Bold)
            }
            Text("⋯",color=PrimaryText,fontSize=17.sp,fontWeight=FontWeight.Bold,modifier=Modifier.align(Alignment.TopStart).padding(7.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xCC0A1220)).clickable{options()}.padding(horizontal=8.dp,vertical=2.dp))
            if(fav) Text("★",color=Color(0xFFFFD36E),fontSize=16.sp,modifier=Modifier.align(Alignment.TopEnd).padding(8.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(app.name,color=PrimaryText,fontSize=12.sp,fontWeight=FontWeight.SemiBold,maxLines=1,overflow=TextOverflow.Ellipsis)
        Text(if(app.isGame) "GAME" else "APP",color=Muted,fontSize=8.sp,letterSpacing=1.2.sp)
    }
}

@Composable private fun Empty(title:String,subtitle:String) {
    Column(Modifier.fillMaxWidth().padding(vertical=35.dp),horizontalAlignment=Alignment.CenterHorizontally) {
        Text("◇",color=Accent,fontSize=34.sp)
        Text(title,color=PrimaryText,fontSize=15.sp,fontWeight=FontWeight.SemiBold)
        Text(subtitle,color=Muted,fontSize=11.sp)
    }
}

@Composable private fun Rail(title:String,items:List<AppItem>,store:Store,wide:Boolean,play:(AppItem)->Unit,options:(AppItem)->Unit) {
    if(items.isEmpty()) return
    Column {
        Text(title,color=PrimaryText,fontSize=18.sp,fontWeight=FontWeight.SemiBold,modifier=Modifier.padding(bottom=12.dp))
        LazyRow(horizontalArrangement=Arrangement.spacedBy(14.dp)) { items(items,key={it.pkg}) { a -> GameCard(a,a.pkg in store.favorites,wide,{play(a)},{options(a)}) } }
    }
}

@Composable private fun Home(apps:List<AppItem>,store:Store,wide:Boolean,play:(AppItem)->Unit,options:(AppItem)->Unit,openLibrary:()->Unit) {
    val recent=store.recent.mapNotNull { p -> apps.find{it.pkg==p} }
    val featured=recent.firstOrNull() ?: apps.firstOrNull()
    val favs=apps.filter{it.pkg in store.favorites}
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=if(wide)34.dp else 20.dp,vertical=20.dp)) {
        Heading("YOUR GAME SPACE","Mainkan sesuatu",apps.size.toString()+" GAME")
        if(featured==null) Empty("Library masih kosong","Tambahkan game untuk mulai membangun koleksimu.")
        else Box(Modifier.fillMaxWidth().height(if(wide)270.dp else 218.dp).clip(RoundedCornerShape(25.dp)).background(Panel)) {
            Image(featured.icon,null,contentScale=ContentScale.Crop,modifier=Modifier.fillMaxSize().blur(34.dp))
            Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color(0xF5070D18),Color(0xD5070D18),Color(0x55070D18)))))
            Box(Modifier.fillMaxSize().padding(1.dp).clip(RoundedCornerShape(24.dp)).background(Brush.verticalGradient(listOf(Color(0x22FFFFFF),Color.Transparent))))
            Row(Modifier.fillMaxSize().padding(if(wide)27.dp else 17.dp),verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if(recent.isNotEmpty()) "CONTINUE PLAYING" else "FEATURED IN YOUR LIBRARY",color=SwitchMint,fontSize=9.sp,fontWeight=FontWeight.Bold,letterSpacing=1.5.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(featured.name,color=PrimaryText,fontSize=if(wide)27.sp else 20.sp,fontWeight=FontWeight.Bold,maxLines=2,overflow=TextOverflow.Ellipsis)
                    Spacer(Modifier.height(15.dp))
                    Text("▶  MAINKAN",color=Bg,fontSize=11.sp,fontWeight=FontWeight.ExtraBold,letterSpacing=0.6.sp,modifier=Modifier.clip(RoundedCornerShape(10.dp)).background(Brush.horizontalGradient(listOf(SwitchMint,Accent))).clickable{play(featured)}.padding(horizontal=18.dp,vertical=12.dp))
                }
                Spacer(Modifier.width(12.dp))
                Image(featured.icon,featured.name,contentScale=ContentScale.Fit,modifier=Modifier.size(if(wide)168.dp else 116.dp).clip(RoundedCornerShape(21.dp)).background(Panel).padding(7.dp))
            }
        }
        Spacer(Modifier.height(24.dp))
        if(recent.isNotEmpty()) Rail("Lanjutkan bermain",recent,store,wide,play,options)
        if(favs.isNotEmpty()) { Spacer(Modifier.height(22.dp)); Rail("Favorit",favs,store,wide,play,options) }
        Spacer(Modifier.height(24.dp))
        Row(verticalAlignment=Alignment.CenterVertically) {
            Text("Semua game",color=PrimaryText,fontSize=18.sp,fontWeight=FontWeight.SemiBold,modifier=Modifier.weight(1f))
            Text("LIHAT SEMUA  ›",color=Accent,fontSize=9.sp,fontWeight=FontWeight.Bold,modifier=Modifier.clickable{openLibrary()})
        }
        Spacer(Modifier.height(12.dp))
        if(apps.isEmpty()) Empty("Belum ada game","Game yang terdeteksi akan muncul di sini.")
        else LazyRow(horizontalArrangement=Arrangement.spacedBy(14.dp)){items(apps.take(12),key={it.pkg}){a->GameCard(a,a.pkg in store.favorites,wide,{play(a)},{options(a)})}}
    }
}

@Composable private fun Library(apps:List<AppItem>,store:Store,wide:Boolean,play:(AppItem)->Unit,options:(AppItem)->Unit,add:()->Unit) {
    var filter by rememberSaveable{mutableStateOf("Semua")}
    var reverse by rememberSaveable{mutableStateOf(false)}
    val result=apps.filter{when(filter){"Game"->it.isGame;"Aplikasi"->!it.isGame;"Favorit"->it.pkg in store.favorites;else->true}}.let{if(reverse)it.sortedByDescending{a->a.name.lowercase()}else it.sortedBy{a->a.name.lowercase()}}
    Column(Modifier.fillMaxSize().padding(horizontal=if(wide)30.dp else 19.dp,vertical=22.dp)) {
        Heading("YOUR COLLECTION","Koleksi",result.size.toString()+" ITEM")
        Row(horizontalArrangement=Arrangement.spacedBy(6.dp),verticalAlignment=Alignment.CenterVertically) {
            listOf("Semua","Game","Aplikasi","Favorit").forEach{f->Text(f,color=if(filter==f)Bg else Muted,fontSize=9.sp,fontWeight=FontWeight.SemiBold,modifier=Modifier.clip(RoundedCornerShape(40.dp)).background(if(filter==f)Accent else Panel).clickable{filter=f}.padding(horizontal=10.dp,vertical=8.dp))}
            Spacer(Modifier.weight(1f))
            Text("⇅",color=Accent,fontSize=18.sp,modifier=Modifier.clickable{reverse=!reverse})
        }
        Spacer(Modifier.height(14.dp))
        if(result.isEmpty()) { Empty("Tidak ada hasil","Ubah filter atau tambahkan aplikasi."); Text("+ Tambah aplikasi",color=Accent,modifier=Modifier.align(Alignment.CenterHorizontally).clickable{add()}) }
        else LazyVerticalGrid(columns=GridCells.Adaptive(if(wide)145.dp else 108.dp),contentPadding=PaddingValues(bottom=24.dp),horizontalArrangement=Arrangement.spacedBy(14.dp),verticalArrangement=Arrangement.spacedBy(17.dp),modifier=Modifier.weight(1f)){items(result,key={it.pkg}){a->GameCard(a,a.pkg in store.favorites,wide,{play(a)},{options(a)})}}
    }
}

@Composable private fun Search(apps:List<AppItem>,store:Store,wide:Boolean,play:(AppItem)->Unit,options:(AppItem)->Unit) {
    var query by rememberSaveable{mutableStateOf("")}
    val result=apps.filter{it.name.contains(query,true)||it.pkg.contains(query,true)}
    Column(Modifier.fillMaxSize().padding(horizontal=if(wide)30.dp else 19.dp,vertical=22.dp)) {
        Heading("FIND YOUR GAME","Pencarian",result.size.toString()+" HASIL")
        OutlinedTextField(query,{query=it},modifier=Modifier.fillMaxWidth(),singleLine=true,placeholder={Text("Nama game atau aplikasi…")})
        Spacer(Modifier.height(15.dp))
        if(result.isEmpty()) Empty("Tidak ditemukan","Coba kata kunci lain.")
        else LazyVerticalGrid(columns=GridCells.Adaptive(if(wide)145.dp else 108.dp),contentPadding=PaddingValues(bottom=24.dp),horizontalArrangement=Arrangement.spacedBy(14.dp),verticalArrangement=Arrangement.spacedBy(17.dp),modifier=Modifier.weight(1f)){items(result,key={it.pkg}){a->GameCard(a,a.pkg in store.favorites,wide,{play(a)},{options(a)})}}
    }
}

@Composable private fun Settings(add:()->Unit,reset:()->Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
        Heading("PERSONALIZE","Pengaturan")
        Text("LIBRARY",color=Accent,fontSize=9.sp,fontWeight=FontWeight.Bold)
        SettingsAction("Tambah aplikasi","Pilih aplikasi yang ingin ditampilkan",add)
        SettingsAction("Pulihkan deteksi otomatis","Batalkan perubahan daftar manual. Favorit dan riwayat tetap disimpan.",reset)
        Spacer(Modifier.height(20.dp))
        Text("GameLauncher 2.0",color=PrimaryText,fontSize=17.sp,fontWeight=FontWeight.SemiBold)
        Text("Adaptive console-style Android launcher",color=Muted,fontSize=11.sp)
    }
}
@Composable private fun SettingsAction(title:String,description:String,click:()->Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical=7.dp).clip(RoundedCornerShape(13.dp)).background(Panel).clickable{click()}.padding(15.dp)) {
        Text(title,color=PrimaryText,fontSize=13.sp,fontWeight=FontWeight.SemiBold)
        Text(description,color=Muted,fontSize=10.sp)
    }
}

@Composable private fun Detail(app:AppItem,fav:Boolean,play:()->Unit,toggle:()->Unit,remove:()->Unit,back:()->Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
        Text("‹  KEMBALI KE KOLEKSI",color=SwitchMint,fontSize=10.sp,fontWeight=FontWeight.Bold,letterSpacing=1.sp,modifier=Modifier.clickable{back()}.padding(bottom=19.dp))
        Box(Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(22.dp))) {
            Image(app.icon,null,contentScale=ContentScale.Crop,modifier=Modifier.fillMaxSize().blur(30.dp))
            Box(Modifier.fillMaxSize().background(Color(0xB908101D)))
            Image(app.icon,app.name,contentScale=ContentScale.Fit,modifier=Modifier.align(Alignment.Center).size(145.dp).clip(RoundedCornerShape(20.dp)))
        }
        Spacer(Modifier.height(18.dp))
        Text(app.name,color=PrimaryText,fontSize=25.sp,fontWeight=FontWeight.Bold)
        Text(if(app.isGame)"GAME ANDROID" else "APLIKASI ANDROID",color=Accent,fontSize=9.sp,letterSpacing=1.4.sp)
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement=Arrangement.spacedBy(9.dp)) {
            Text("▶  MAINkan",color=Bg,fontSize=11.sp,fontWeight=FontWeight.Bold,modifier=Modifier.clip(RoundedCornerShape(9.dp)).background(Accent).clickable{play()}.padding(14.dp))
            Text(if(fav)"★ FAVORIT" else "☆ FAVORIT",color=PrimaryText,fontSize=11.sp,modifier=Modifier.clip(RoundedCornerShape(9.dp)).background(Raised).clickable{toggle()}.padding(14.dp))
        }
        Spacer(Modifier.height(12.dp))
        Text("HAPUS DARI LAUNCHER",color=Color(0xFFFF9B9B),fontSize=11.sp,fontWeight=FontWeight.SemiBold,modifier=Modifier.clip(RoundedCornerShape(9.dp)).background(Color(0xFF3A2028)).clickable{remove()}.padding(horizontal=14.dp,vertical=12.dp))
        Text("Game tetap terpasang di perangkat. Tindakan ini hanya menghapusnya dari daftar GameLauncher.",color=Muted,fontSize=10.sp,modifier=Modifier.padding(top=7.dp))
        Spacer(Modifier.height(18.dp))
        Text("PACKAGE NAME",color=Muted,fontSize=9.sp)
        Text(app.pkg,color=PrimaryText,fontSize=12.sp)
    }
}

@Composable private fun AddDialog(apps:List<AppItem>,add:(AppItem)->Unit,close:()->Unit) {
    AlertDialog(onDismissRequest=close,title={Text("Tambah aplikasi")},text={
        if(apps.isEmpty()) Text("Tidak ada aplikasi lain untuk ditambahkan.")
        else Column(Modifier.heightIn(max=350.dp).verticalScroll(rememberScrollState())){apps.forEach{a->Row(Modifier.fillMaxWidth().clickable{add(a)}.padding(vertical=7.dp),verticalAlignment=Alignment.CenterVertically){Image(a.icon,a.name,Modifier.size(38.dp));Spacer(Modifier.width(10.dp));Text(a.name,maxLines=1,overflow=TextOverflow.Ellipsis)}}}
    },confirmButton={TextButton(onClick=close){Text("Selesai")}})
}

@Composable private fun LauncherApp(all:List<AppItem>) {
    val ctx=LocalContext.current
    val store=remember{Store(ctx)}
    var pageName by rememberSaveable{mutableStateOf(Page.HOME.name)}
    val page=Page.values().firstOrNull{it.name==pageName}?:Page.HOME
    var selectedPkg by rememberSaveable{mutableStateOf<String?>(null)}
    var picker by remember{mutableStateOf(false)}
    var reset by remember{mutableStateOf(false)}
    var removeTarget by remember{mutableStateOf<AppItem?>(null)}
    val visible=all.filter{(it.isGame||it.pkg in store.added)&&it.pkg !in store.removed}
    val selected=all.firstOrNull{it.pkg==selectedPkg}
    val play:(AppItem)->Unit={a->ctx.packageManager.getLaunchIntentForPackage(a.pkg)?.let{store.played(a.pkg);ctx.startActivity(it)}}
    val options:(AppItem)->Unit={selectedPkg=it.pkg}
    BoxWithConstraints(Modifier.fillMaxSize().background(Bg)) {
        val wide=maxWidth>=700.dp || maxWidth>maxHeight
        Row(Modifier.fillMaxSize().safeDrawingPadding()) {
            if(wide) Navigation(page,true){pageName=it.name}
            Column(Modifier.weight(1f).fillMaxHeight().background(Brush.verticalGradient(listOf(Color(0xFF0B1422),Bg)))) {
                Row(Modifier.fillMaxWidth().height(58.dp).padding(horizontal=if(wide)30.dp else 19.dp),verticalAlignment=Alignment.CenterVertically) {
                    Text("◈",color=SwitchMint,fontSize=20.sp,fontWeight=FontWeight.Black)
                    Spacer(Modifier.width(9.dp))
                    Text("GAME SPACE",color=PrimaryText,fontSize=11.sp,fontWeight=FontWeight.ExtraBold,letterSpacing=1.6.sp,modifier=Modifier.weight(1f))
                    Text("● READY",color=SwitchMint,fontSize=9.sp,fontWeight=FontWeight.Bold,letterSpacing=1.sp,modifier=Modifier.clip(RoundedCornerShape(30.dp)).background(Color(0x2227C99A)).padding(horizontal=10.dp,vertical=6.dp))
                }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    if(selected!=null) Detail(selected,selected.pkg in store.favorites,{play(selected);selectedPkg=null},{store.favorite(selected.pkg)},{removeTarget=selected},{selectedPkg=null})
                    else when(page) {
                        Page.HOME->Home(visible,store,wide,play,options){pageName=Page.LIBRARY.name}
                        Page.LIBRARY->Library(visible,store,wide,play,options){picker=true}
                        Page.SEARCH->Search(visible,store,wide,play,options)
                        Page.SETTINGS->Settings({picker=true},{reset=true})
                    }
                }
                if(!wide) Navigation(page,false){pageName=it.name}
            }
        }
    }
    if(picker) AddDialog(all.filter{a->visible.none{it.pkg==a.pkg}},{store.add(it.pkg)},{picker=false})
    if(reset) AlertDialog(onDismissRequest={reset=false},title={Text("Pulihkan daftar otomatis?")},text={Text("Daftar tambah/hapus manual akan direset. Favorit dan riwayat tetap disimpan.")},confirmButton={TextButton(onClick={store.reset();reset=false}){Text("Pulihkan")}},dismissButton={TextButton(onClick={reset=false}){Text("Batal")}})
    removeTarget?.let { target ->
        AlertDialog(
            onDismissRequest={removeTarget=null},
            title={Text("Hapus dari GameLauncher?")},
            text={Text("${target.name} akan dihilangkan dari daftar launcher. Aplikasi tetap terpasang dan datanya tidak dihapus.")},
            confirmButton={TextButton(onClick={store.remove(target.pkg);removeTarget=null;selectedPkg=null}){Text("Hapus dari daftar",color=Color(0xFFFF9B9B))}},
            dismissButton={TextButton(onClick={removeTarget=null}){Text("Batal")}}
        )
    }
}

class MainActivity:ComponentActivity() {
    override fun onCreate(savedInstanceState:Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme=darkColorScheme(background=Bg,surface=Panel,primary=Accent,onBackground=PrimaryText,onSurface=PrimaryText)) {
                val ctx=LocalContext.current
                val apps by produceState<List<AppItem>?>(null){value=withContext(Dispatchers.Default){loadAllApps(ctx)}}
                if(apps==null) Box(Modifier.fillMaxSize().background(Bg)) else LauncherApp(apps!!)
            }
        }
    }
}