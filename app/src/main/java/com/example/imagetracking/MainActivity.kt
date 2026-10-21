package com.example.imagetracking

import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
import android.os.Bundle
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.ar.core.AugmentedImage
import com.google.ar.core.AugmentedImageDatabase
import com.google.ar.core.Config
import com.google.ar.core.Frame
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberMaterialLoader
import io.github.sceneview.rememberModelInstance
import io.github.sceneview.rememberModelLoader

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                ArImageTrackerScreen()
            }
        }
    }
}

@Composable
private fun ArImageTrackerScreen() {
    val context = LocalContext.current

    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val materialLoader = rememberMaterialLoader(engine)

    // Эталонная картинка. Можно заменить на загрузку из assets:
     val referenceBitmap = remember {
         context.assets.open("miku.png").use(BitmapFactory::decodeStream)
     }

    // Модель. Если файла нет — ниже используется CubeNode.
    val modelInstance = rememberModelInstance(
        modelLoader = modelLoader,
        fileLocation = "models/miku_plush.glb"
    )

    val cubeMaterial = remember(materialLoader) {
        materialLoader.createColorInstance(AndroidColor.rgb(255, 140, 0))
    }

    var detectedImages by remember {
        mutableStateOf<List<AugmentedImage>>(emptyList())
    }

    var isExpanded by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {

        ARSceneView(
            modifier = Modifier.fillMaxSize(),
            engine = engine,
            modelLoader = modelLoader,
            materialLoader = materialLoader,
            planeRenderer = false,

            // Создаём AugmentedImageDatabase и регистрируем эталонную картинку.
            sessionConfiguration = { session: Session, config: Config ->
                config.augmentedImageDatabase =
                    AugmentedImageDatabase(session).apply {
                        addImage(
                            "miku",
                            referenceBitmap,
                            0.15f // физическая ширина маркера в метрах
                        )
                    }
                config.focusMode = Config.FocusMode.AUTO
                config.planeFindingMode = Config.PlaneFindingMode.DISABLED
            },

            // Получаем только реально отслеживаемые изображения.
            onSessionUpdated = { _: Session, frame: Frame ->
                val images = frame
                    .getUpdatedTrackables(AugmentedImage::class.java)
                    .filter { it.trackingState == TrackingState.TRACKING }

                if (images != detectedImages) {
                    detectedImages = images
                }
            },

            // Взаимодействие по касанию экрана:
            // увеличиваем/поворачиваем объект.
            onTouchEvent = { event: MotionEvent, _ ->
                if (event.action == MotionEvent.ACTION_UP) {
                    isExpanded = !isExpanded
                    true
                } else {
                    false
                }
            }
        ) {
            detectedImages.forEach { image ->
                key(image.name) {
                    AugmentedImageNode(
                        augmentedImage = image,
                        applyImageScale = false,
                        apply = { this.isVisible = image.trackingMethod == AugmentedImage.TrackingMethod.FULL_TRACKING }
                    ) {
                        if (modelInstance != null) {
                            ModelNode(
                                modelInstance = modelInstance,
                                scaleToUnits = if (isExpanded) 0.30f else 0.18f,
                                position = Position(0f, 0f, 0f),
                                rotation = Rotation(
                                    y = if (isExpanded) 45f else 0f
                                )
                            )
                        } else {
                            // Fallback, если GLB-файл не добавлен в assets.
                            CubeNode(
                                size = dev.romainguy.kotlin.math.Float3(
                                    if (isExpanded) 0.20f else 0.10f,
                                    if (isExpanded) 0.20f else 0.10f,
                                    if (isExpanded) 0.20f else 0.10f
                                ),
                                materialInstance = cubeMaterial,
                                position = Position(0f, 0.10f, 0f),
                                rotation = Rotation(
                                    y = if (isExpanded) 45f else 0f
                                )
                            )
                        }
                    }
                }
            }
        }

            // Подсказка и превью эталонного изображения.
            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
                    .background(
                        color = ComposeColor.Black.copy(alpha = 0.55f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(12.dp)
            ) {
                Text(
                    text = "Наведите камеру на эталонное изображение",
                    color = ComposeColor.White
                )

                Image(
                    bitmap = referenceBitmap.asImageBitmap(),
                    contentDescription = "Эталонное изображение",
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .size(120.dp)
                )

                Text(
                    text = if (detectedImages.isEmpty()) {
                        "Поиск метки…"
                    } else {
                        "Метка найдена. Коснитесь экрана."
                    },
                    color = ComposeColor.White,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
    }
}