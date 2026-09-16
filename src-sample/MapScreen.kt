package com.meuboi.fazendamaracatiara.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Color as AndroidColor
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight

import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.MapsInitializer
import com.google.android.gms.maps.MapsInitializer.Renderer
import com.google.android.gms.maps.model.*
import com.google.maps.android.compose.*
import com.meuboi.fazendamaracatiara.R
import com.meuboi.fazendamaracatiara.farmdata.FarmLocation
import com.meuboi.fazendamaracatiara.model.geo.*
import com.meuboi.fazendamaracatiara.ui.components.POICreationDialog
import com.meuboi.fazendamaracatiara.ui.components.POIDetailsDialog
import com.meuboi.fazendamaracatiara.ui.viewmodel.MapViewModel
import com.meuboi.fazendamaracatiara.ui.viewmodel.POIViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Tela de Mapa que exibe os marcos geográficos e benfeitorias da fazenda.
 */
@Composable
fun MapScreen(
    snackbarHostState: SnackbarHostState,
    viewModel: MapViewModel = viewModel(),
    poiViewModel: POIViewModel = viewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Sync pending POIs when map opens
    LaunchedEffect(Unit) {
        poiViewModel.syncPendingPOIs(context) { message ->
            scope.launch {
                snackbarHostState.showSnackbar(message)
            }
        }
    }

    // Launcher para tirar foto
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (!success) {
            poiViewModel.capturedPhotoUri = null
        }
    }

    // Controle da bússola
    LaunchedEffect(Unit) {
        poiViewModel.startCompass(context)
    }
    
    DisposableEffect(Unit) {
        onDispose {
            poiViewModel.stopCompass()
        }
    }

    // Estado para permissão de localização
    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    // Launcher para solicitar permissões
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasLocationPermission = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
    }

    // Solicita permissão se ainda não foi concedida
    LaunchedEffect(Unit) {
        if (!hasLocationPermission) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }
    
    // Estados para controle de UI
    var mapType by remember { mutableStateOf(MapType.TERRAIN) }
    var selectedPOI by remember { mutableStateOf<POI?>(null) }
    var marcoIcon by remember { mutableStateOf<BitmapDescriptor?>(null) }
    var homeIcon by remember { mutableStateOf<BitmapDescriptor?>(null) }
    var pickIcon by remember { mutableStateOf<BitmapDescriptor?>(null) }
    var corralIcon by remember { mutableStateOf<BitmapDescriptor?>(null) }
    var poiCameraIcon by remember { mutableStateOf<BitmapDescriptor?>(null) }

    var poiPinIcon by remember { mutableStateOf<BitmapDescriptor?>(null) }
    var poiCarcacaIcon by remember { mutableStateOf<BitmapDescriptor?>(null) }
    var poiIncidentIcon by remember { mutableStateOf<BitmapDescriptor?>(null) }
    var poiPastureIcon by remember { mutableStateOf<BitmapDescriptor?>(null) }
    var poiBugIcon by remember { mutableStateOf<BitmapDescriptor?>(null) }

    // Estados para medição de distância
    var isMeasuring by remember { mutableStateOf(false) }
    var measurePoints by remember { mutableStateOf<List<LatLng>>(emptyList()) }
    val distance = remember(measurePoints) {
        if (measurePoints.size >= 2) {
            val results = FloatArray(1)
            android.location.Location.distanceBetween(
                measurePoints[0].latitude, measurePoints[0].longitude,
                measurePoints[1].latitude, measurePoints[1].longitude,
                results
            )
            results[0]
        } else 0f
    }

    // Estado que controla a posição da câmera do Google Maps
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(-6.445274553459215, -51.45076421140827), 13.5f)
    }

    // Lógica para focar em uma localização específica se solicitado
    LaunchedEffect(viewModel.requestedFocusLocation) {
        viewModel.requestedFocusLocation?.let { location ->
            cameraPositionState.animate(
                CameraUpdateFactory.newLatLngZoom(location, 17f)
            )
            viewModel.clearFocusRequest()
        }
    }

    // Cache de ícones de texto
    val perimeterIcons = remember(viewModel.perimeters) {
        viewModel.perimeters.associate { perimeter ->
            perimeter.id to createTextIcon(perimeter.name ?: "Fazenda", AndroidColor.parseColor("#2E7D32"), 45f)
        }
    }

    val pastureLabelsSmall = remember(viewModel.pastures) {
        viewModel.pastures.associate { pasture ->
            pasture.id to createTextIcon(pasture.name.toString(), AndroidColor.parseColor("#1B5E20"), 28f)
        }
    }

    val pastureLabelsLarge = remember(viewModel.pastures) {
        viewModel.pastures.associate { pasture ->
            pasture.id to createTextIcon(pasture.name.toString(), AndroidColor.parseColor("#1B5E20"), 35f)
        }
    }

    val contourIcons = remember(viewModel.contours) {
        viewModel.contours.mapNotNull { it.elevation }.distinct().associateWith { elevation ->
            createTextIcon("$elevation m", AndroidColor.parseColor("#757575"), 22f, false)
        }
    }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            MapsInitializer.initialize(context, Renderer.LATEST) { _ -> }
        }
        
        marcoIcon = createBitmapIcon(context, R.drawable.marco_black_24px, 0.5f, AndroidColor.rgb(112, 108, 102))
        homeIcon = createBitmapIcon(context, R.drawable.home_24px, 0.7f, AndroidColor.rgb(107, 66, 12))
        corralIcon = createBitmapIcon(context, R.drawable.fence_24px, 0.7f, AndroidColor.rgb(107, 66, 12))
        pickIcon = createBitmapIcon(context, R.drawable.arrow_drop_up_24px, 0.6f, AndroidColor.rgb(55, 102, 63))
        poiCameraIcon = createBitmapIcon(context, R.drawable.local_see_24px, 0.6f, AndroidColor.rgb(0, 105, 92))

        poiPinIcon = createBitmapIcon(context, R.drawable.location_on_24px, 0.65f, AndroidColor.rgb(0, 105, 92))
        poiCarcacaIcon = createBitmapIcon(context, R.drawable.skeleton_24px, 0.6f, AndroidColor.rgb(158, 138, 144))
        poiIncidentIcon = createBitmapIcon(context, R.drawable.emergency_share_24px, 0.8f, AndroidColor.rgb(179, 49, 23))
        poiPastureIcon = createBitmapIcon(context, R.drawable.grass_24px, 0.7f, AndroidColor.rgb(76, 156, 95))
        poiBugIcon = createBitmapIcon(context, R.drawable.pest_control_24px, 0.6f, AndroidColor.rgb(92, 81, 64))





        viewModel.loadMapFeatures(context)
    }

    val ZOOM_VER_WATER = 13.0f
    val ZOOM_VER_PICKS = 14.0f
    val ZOOM_VER_VEGETATION = 13.5f
    val ZOOM_VER_ROADS = 13.5f
    val ZOOM_VER_PATHS = 14.5f
    val ZOOM_VER_PASTURES = 14.0f
    val ZOOM_VER_PASTURES_LABELS = 14.5f
    val ZOOM_VER_CONTOURS = 14.0f
    val ZOOM_VER_STRUCTURES = 14.5f
    val ZOOM_VER_MARCOS = 15.0f
    val ZOOM_VER_POIS = 15.5f

    val currentZoom = cameraPositionState.position.zoom

    // Função auxiliar para lidar com cliques de medição (pode ser chamada pelo mapa ou marcadores)
    val handleMeasureClick = { latLng: LatLng ->
        if (isMeasuring) {
            if (measurePoints.size >= 2) {
                measurePoints = emptyList()
            } else {
                measurePoints = measurePoints + latLng
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(
                mapType = mapType,
                isMyLocationEnabled = hasLocationPermission
            ),
            uiSettings = MapUiSettings(
                myLocationButtonEnabled = hasLocationPermission
            ),
            onMapClick = { latLng ->
                if (isMeasuring) {
                    handleMeasureClick(latLng)
                }
            }
        ) {
            // Renderiza o Perímetro
            viewModel.perimeters.forEach { perimeter ->
                Polygon(
                    points = perimeter.points,
                    fillColor = ComposeColor(0x224CAF50),
                    strokeColor = ComposeColor(0xFF2E7D32),
                    strokeWidth = 2f
                )
                if (perimeter.points.isNotEmpty()) {
                    val center = LatLng(perimeter.points.map { it.latitude }.average(), perimeter.points.map { it.longitude }.average())
                    Marker(
                        state = MarkerState(position = center),
                        title = perimeter.name ?: "Fazenda",
                        snippet = "${perimeter.hectares} ha | ${perimeter.alqueires} alq",
                        icon = perimeterIcons[perimeter.id],
                        anchor = androidx.compose.ui.geometry.Offset(0.5f, 0.5f),
                        onClick = {
                            if (isMeasuring) {
                                handleMeasureClick(it.position)
                                true
                            } else false
                        }
                    )
                }
            }

            // Vegetações
            if (currentZoom >= ZOOM_VER_VEGETATION) {
                viewModel.vegetations.forEach { vegetation ->
                    val type = vegetation.type?.lowercase()
                    val fillColor = when (type) {
                        "nativa" -> ComposeColor(0x490c750f)
                        "recuperada" -> ComposeColor(0x264CAF50)
                        "floresta" -> ComposeColor(0x594CAF50)
                        else -> ComposeColor(0x598caf4c)
                    }
                    Polygon(
                        points = vegetation.points,
                        fillColor = fillColor,
                        strokeColor = ComposeColor.Transparent,
                        strokeWidth = 0f
                    )
                }
            }

            // Pastos
            if (currentZoom >= ZOOM_VER_PASTURES) {
                viewModel.pastures.forEach { pasture ->
                    Polygon(
                        points = pasture.points,
                        fillColor = ComposeColor(0x1A4CAF50),
                        strokeColor = ComposeColor(0xFF8a695b),
                        strokeWidth = 2f,
                        strokePattern = listOf(Dash(6f), Gap(20f))
                    )
                    if(currentZoom > ZOOM_VER_PASTURES_LABELS)
                        if (pasture.points.isNotEmpty()) {
                            val icon = if (cameraPositionState.position.zoom >= 15.5f) {
                                pastureLabelsLarge[pasture.id]
                            } else {
                                pastureLabelsSmall[pasture.id]
                            }
                            val center = LatLng(pasture.points.map { it.latitude }.average(), pasture.points.map { it.longitude }.average())
                            Marker(
                                state = MarkerState(position = center),
                                title = "Pasto d@(s) ${pasture.name}",
                                snippet = "%.1f ha | %.1f alq".format(pasture.hectares, pasture.alqueires),
                                icon = icon,
                                anchor = androidx.compose.ui.geometry.Offset(0.5f, 0.5f),
                                onClick = {
                                    if (isMeasuring) {
                                        handleMeasureClick(it.position)
                                        true
                                    } else false
                                }
                            )
                        }
                }
            }

            // Caminhos e Estradas
            if (currentZoom >= ZOOM_VER_ROADS) {
                viewModel.paths.forEach { path ->
                    val typeLower = path.type?.lowercase()
                    val (lineColor, lineWidth, strokePattern) = when (typeLower) {
                        "estrada" -> Triple(ComposeColor(0xFA8C4322), 8f, null)
                        "corredor" -> Triple(ComposeColor(0x66E38B94), 10f, null)
                        "caminho" -> Triple(ComposeColor(0xFAF99A7D), 4f, listOf(Dash(10f), Gap(28f)))
                        else -> Triple(ComposeColor(0xFA7A452d), 5f, null)
                    }
                    if (!(typeLower == "caminho" || typeLower == "corredor") || currentZoom >= ZOOM_VER_PATHS) {
                        Polyline(points = path.points, color = lineColor, width = lineWidth, pattern = strokePattern)
                    }
                }
            }

            // Estradas (Street Network)
            if (currentZoom < ZOOM_VER_ROADS && currentZoom > 8.5f) {
                viewModel.streets.forEach { street ->
                    Polyline(
                        points = street.points,
                        color = ComposeColor(0xFA8C4322),
                        width = 4f,
                        geodesic = true
                    )
                }
            }

            // Cursos d'Água
            if (currentZoom >= ZOOM_VER_WATER) {
                viewModel.streams.forEach { stream ->

                    val width = if (stream.flowLevel >= 2f) 6f else if(stream.flowLevel == 1) 4f else  2f

                    Polyline(points = stream.points, color = ComposeColor(0xFFa5bfdd), width = width)
                }
                viewModel.waterBodies.forEach { waterBody ->
                    Polygon(points = waterBody.points, fillColor = ComposeColor(0xFFa5bfdd), strokeColor = ComposeColor(0xFF6498d2), strokeWidth = 0.5f)
                }
            }

            // Curvas de Nível
            if (currentZoom >= ZOOM_VER_CONTOURS) {
                viewModel.contours.forEach { contour ->
                    Polyline(points = contour.points, color = ComposeColor(0xFFa0a0b0), width = 0.8f)
                    if (currentZoom >= 16.0f && contour.elevation != null && contour.points.isNotEmpty()) {
                        Marker(
                            state = MarkerState(position = contour.points[contour.points.size / 2]),
                            icon = contourIcons[contour.elevation],
                            anchor = androidx.compose.ui.geometry.Offset(0.5f, 0.5f),
                            flat = true,
                            onClick = {
                                if (isMeasuring) {
                                    handleMeasureClick(it.position)
                                    true
                                } else false
                            }
                        )
                    }
                }
            }

            // Marcos
            if (currentZoom >= ZOOM_VER_MARCOS) {
                viewModel.marcos.forEach { marco ->
                    Marker(
                        state = MarkerState(position = marco.point),
                        title = "Estação: ${marco.name}",
                        snippet = "ID: ${marco.id}",
                        icon = marcoIcon,
                        anchor = androidx.compose.ui.geometry.Offset(0.5f, 0.5f),
                        onClick = {
                            if (isMeasuring) {
                                handleMeasureClick(it.position)
                                true
                            } else false
                        }
                    )
                }
            }

            // POIs
            if (currentZoom >= ZOOM_VER_POIS) {
                // Modo Detalhado
                viewModel.pois.forEach { poi ->
                    val icon = when {
                        poi.name.lowercase().contains("peste") || poi.name.lowercase().contains("cigarrinha") -> poiBugIcon
                        poi.category.lowercase() == "incidente" -> poiIncidentIcon
                        poi.category.lowercase() == "carcaça" -> poiCarcacaIcon
                        poi.category.lowercase() == "pastagem" -> poiPastureIcon
                        poi.isCameraPOI == true -> poiPinIcon
                        else -> poiCameraIcon
                    }
                    Marker(
                        state = MarkerState(position = poi.location),
                        icon = icon,
                        onClick = {
                            if (isMeasuring) {
                                handleMeasureClick(it.position)
                            } else {
                                selectedPOI = poi
                            }
                            true // Indica que o clique foi tratado
                        },
                        anchor = androidx.compose.ui.geometry.Offset(0.5f, 0.5f)
                    )
                }
            } else if (currentZoom > 10.0f){
                // Modo Resumo
                viewModel.poisLowScale.forEach { poi ->
                    val icon = if (poi.category.lowercase() == "incidente") poiIncidentIcon else poiPinIcon
                    Marker(
                        state = MarkerState(position = poi.location),
                        icon = icon,
                        onClick = {
                            if (isMeasuring) {
                                handleMeasureClick(it.position)
                            } else {
                                scope.launch {
                                    cameraPositionState.animate(
                                        CameraUpdateFactory.newLatLngZoom(poi.location, 16.5f)
                                    )
                                }
                            }
                            true
                        },
                        anchor = androidx.compose.ui.geometry.Offset(0.5f, 0.5f)
                    )
                }


            }

            if (currentZoom >= ZOOM_VER_PICKS) {
                viewModel.picks.forEach { pick ->
                    Marker(
                        state = MarkerState(position = pick.point),
                        title = "Pico de Morro",
                        snippet = "Altitude: ${pick.height}",
                        icon = pickIcon,
                        anchor = androidx.compose.ui.geometry.Offset(0.5f, 0.5f),
                        onClick = {
                            if (isMeasuring) {
                                handleMeasureClick(it.position)
                                true
                            } else false
                        }
                    )
                }
            }

                        // Benfeitorias
            if (currentZoom >= ZOOM_VER_STRUCTURES) {
                viewModel.structures.forEach { structure ->
                    val icon = if (structure.type?.lowercase() == "curral") corralIcon else homeIcon
                    Marker(
                        state = MarkerState(position = structure.point),
                        title = structure.name ?: structure.type,
                        snippet = "Tipo: ${structure.type}\nFazenda: ${structure.farm}",
                        icon = icon,
                        anchor = androidx.compose.ui.geometry.Offset(0.5f, 0.5f),
                        onClick = {
                            if (isMeasuring) {
                                handleMeasureClick(it.position)
                                true
                            } else false
                        }
                    )
                }
            }


            // Renderiza a linha de medição
            if (isMeasuring && measurePoints.isNotEmpty()) {
                measurePoints.forEachIndexed { index, point ->
                    Marker(
                        state = MarkerState(position = point),
                        icon = marcoIcon,
                        anchor = androidx.compose.ui.geometry.Offset(0.5f, 0.5f)
                        //icon = BitmapDescriptorFactory.defaultMarker(if (index == 0) BitmapDescriptorFactory.HUE_AZURE else BitmapDescriptorFactory.HUE_RED),
                        //alpha = 0.8f
                    )
                }
                if (measurePoints.size >= 2) {
                    Polyline(
                        points = measurePoints,
                        color = ComposeColor(0xFFa89d32),
                        width = 4f,
                        geodesic = true,
                        pattern = listOf(Dash(20f), Gap(10f))
                    )
                }
            }


        }

        // Botões de Ação
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Botão Pin POI (Manual no Centro)
            FloatingActionButton(
                onClick = {
                    val center = cameraPositionState.position.target
                    poiViewModel.name = ""
                    poiViewModel.description = ""
                    poiViewModel.category = "outro"
                    poiViewModel.poiMode = POIViewModel.POIMode.MANUAL_PIN
                    // Trigger dialog manually without photo
                    poiViewModel.pinnedLocation = cameraPositionState.position.target
                    //poiViewModel.capturedPhotoUri = Uri.parse("manual://pin?lat=${center.latitude}&lng=${center.longitude}")
                    //poiViewModel.showPOIDialog = true
                    poiViewModel.showSetPointDialog = true
                },
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.add_location_24px),
                    contentDescription = "Pin POI no centro"
                )
            }

            // Botão Photo POI
            FloatingActionButton(
                onClick = {
                    val photoFile = File(context.getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES), "POI_${System.currentTimeMillis()}.jpg")
                    val photoUri = FileProvider.getUriForFile(context, "com.meuboi.fazendamaracatiara.fileprovider", photoFile)
                    poiViewModel.capturedPhotoUri = photoUri
                    cameraLauncher.launch(photoUri)
                    poiViewModel.poiMode = POIViewModel.POIMode.CAMERA
                    poiViewModel.showPOIDialog = true
                },
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.local_see_24px),
                    contentDescription = "Capturar POI"
                )
            }

             // Botão Régua (Medição)
            FloatingActionButton(
                onClick = {
                    isMeasuring = !isMeasuring
                    if (!isMeasuring) measurePoints = emptyList()
                },
                containerColor = if (isMeasuring) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.secondaryContainer,
                contentColor = if (isMeasuring) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onSecondaryContainer
            ) {
                Icon(painter = painterResource(id = R.drawable.measuring_tape_24px), contentDescription = "Medir distância")
            }

            // Botão Atualizar Dados
            FloatingActionButton(
                onClick = { 
                    viewModel.loadMapFeatures(context, forceRefresh = true)
                    poiViewModel.syncPendingPOIs(context) { message ->
                        scope.launch {
                            snackbarHostState.showSnackbar(message)
                        }
                    }
                },
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            ) {
                Icon(painter = painterResource(id = R.drawable.refresh_24px), contentDescription = "Atualizar mapa")
            }

            // Botão Tipo de Mapa
            FloatingActionButton(
                onClick = { mapType = if (mapType == MapType.SATELLITE) MapType.TERRAIN else MapType.SATELLITE },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(painter = painterResource(id = R.drawable.layers_24px), contentDescription = "Tipo de mapa")
            }


        }

        // Card de resultado da medição
        if (isMeasuring) {
            Card(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 64.dp, start = 16.dp, end = 16.dp)
                    .fillMaxWidth(0.7f),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (measurePoints.size < 2) "Selecione dois pontos no mapa" else "Distância Total",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                    if (measurePoints.size >= 2) {
                        Text(
                            text = if (distance >= 1000) "%.2f km".format(distance / 1000) else "%.0f m".format(distance),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(
                        onClick = { 
                            isMeasuring = false
                            measurePoints = emptyList()
                        }
                    ) {
                        Text("Encerrar Medição")
                    }
                }
            }
        }

        /***Confirm POI position Dialog***/
        if (poiViewModel.showSetPointDialog) {
            Icon(
                painter = painterResource(id = R.drawable.marco_black_24px),
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(24.dp))
        }
        if (poiViewModel.showSetPointDialog) {
            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(16.dp)) {
                Column(
                    modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Posicione a localização desejada no centro do mapa")

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = {
                            poiViewModel.showSetPointDialog = false
                        }) {
                            Text("Cancelar")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(onClick = {
                            val center = cameraPositionState.position.target
                            poiViewModel.pinnedLocation = center
                            poiViewModel.poiMode = POIViewModel.POIMode.MANUAL_PIN
                            poiViewModel.showPOIDialog = true
                            poiViewModel.showSetPointDialog = false
                        }) {
                            Text("Confirmar")
                        }
                    }
                }
            }
        }

        // Loading
        if (viewModel.isLoading || poiViewModel.isSaving) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }

        // Dialogo POI (Criação)
        if (poiViewModel.showPOIDialog) {
            POICreationDialog(
                poiViewModel = poiViewModel,
                onDismiss = { poiViewModel.showPOIDialog = false },
                onSave = {
                    poiViewModel.savePOI(
                        context = context,
                        onSuccess = { message ->
                            poiViewModel.showPOIDialog = false
                            scope.launch {
                                snackbarHostState.showSnackbar(message)
                            }
                            // Refresh map to show new POI
                            viewModel.loadMapFeatures(context, forceRefresh = true)
                        },
                        onError = { error ->
                            scope.launch {
                                snackbarHostState.showSnackbar(error)
                            }
                        }
                    )
                }
            )
        }

        // Detalhes do POI (Visualização)
        selectedPOI?.let { poi ->
            POIDetailsDialog(
                poi = poi,
                onDismiss = { selectedPOI = null },
                viewModel = viewModel,
                poiViewModel = poiViewModel,
                snackbarHostState = snackbarHostState
            )
        }
    }
}


private fun createBitmapIcon(context: android.content.Context, resId: Int, scale: Float = 1.0f, tintColor: Int? = null): BitmapDescriptor? {
    val drawable = ContextCompat.getDrawable(context, resId) ?: return null
    tintColor?.let { drawable.setTint(it) }
    val bitmap = Bitmap.createBitmap((drawable.intrinsicWidth * scale).toInt(), (drawable.intrinsicHeight * scale).toInt(), Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    drawable.setBounds(0, 0, canvas.width, canvas.height)
    drawable.draw(canvas)
    return BitmapDescriptorFactory.fromBitmap(bitmap)
}

private fun createTextIcon(text: String, textColor: Int, textSize: Float = 40f, isBold: Boolean = true): BitmapDescriptor {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = textColor; textAlign = Paint.Align.CENTER; this.textSize = textSize; isFakeBoldText = isBold }
    val bounds = android.graphics.Rect()
    paint.getTextBounds(text, 0, text.length, bounds)
    val width = bounds.width() + 10
    val height = bounds.height() + 10
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawText(text, (width / 2).toFloat(), (height / 2 - bounds.centerY()).toFloat(), paint)
    return BitmapDescriptorFactory.fromBitmap(bitmap)
}
