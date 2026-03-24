# 🏃‍♂️ Fitvera - Running Cup

**Fitvera** es una aplicación de entrenamiento orientada al running que permite a los usuarios registrar sus actividades físicas, competir en ligas y conquistar zonas geográficas mediante GPS.

## 🚀 Características principales
- **Registro de actividad:** Seguimiento por GPS de distancia, tiempo, ritmo y desnivel.
- **Visualización de datos:** Gráficos dinámicos de ritmo por kilómetro y mapas de calor del recorrido.
- **Sistema de Gamificación:**
  - **Ligas:** Clasificación mensual basada en el esfuerzo y rendimiento.
  - **Conquista:** Los usuarios pueden "adueñarse" de zonas del mapa al correr por ellas.
- **Social:** Buscador de usuarios y sistema de solicitudes de amistad.
- **Integración con la nube:** Almacenamiento de fotos de entrenamiento mediante Cloudinary y base de datos en tiempo real con Firebase.

## 🛠️ Tecnologías utilizadas
- **Lenguaje:** Kotlin
- **Base de datos:** Firebase Firestore & Auth
- **Mapas:** OSMDroid (OpenStreetMap)
- **Gráficos:** MPAndroidChart
- **Networking:** OkHttp & Cloudinary API
- **Arquitectura:** MVVM (Model-View-ViewModel) con Corrutinas de Kotlin.

## ⚙️ Configuración para Desarrolladores (Maestros)

Para que el proyecto funcione correctamente tras clonarlo, se deben tener en cuenta los siguientes puntos:

1. **Firebase:** El archivo `google-services.json` ya está incluido en la carpeta `/app`.
2. **Dependencias:** Asegúrese de sincronizar el proyecto con Gradle para descargar todas las librerías necesarias (MPAndroidChart, OSMDroid, etc.).
3. **Permisos:** La app requiere permisos de **Ubicación (Fine Location)** y **Cámara/Galería** para funcionar plenamente.

## 👤 Autor
- **Andrés Vera** - *Desarrollador Principal*
- Proyecto desarrollado para DAM.
