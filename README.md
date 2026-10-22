# Iglesia Gestión — Android

Aplicación nativa Android para la gestión de una iglesia. Está escrita en Kotlin
con Jetpack Compose y usa Supabase para autenticación y acceso a los datos.
Este repositorio contiene únicamente el cliente Android; el cliente web y el
backend compartido se mantienen en el
[repositorio principal](https://github.com/kevin9zamora9sanche-cloud/IglesiaGestiNAdministrativa).

## Funcionalidad

- Inicio de sesión, cierre y restauración de sesión con Supabase Auth.
- Consulta del perfil, rol y estado de cuenta del usuario.
- Inicio "Mis pendientes" con trabajo propio con fecha y avisos sin leer.
- Agenda general y personal con semana navegable, filtros por tipo y detalle
  (responsables, invitados, predicador, director, tareas y repertorio).
- Actividades con tipo y clasificación (ministerio, proyecto, general o
  individual), tareas hijas con prioridad, fecha límite y comentarios, y
  cambio de estado (Por hacer, En curso, En pausa, Completada).
- Proyectos institucionales con edición completa y avance automático.
- Repertorios de cultos y catálogo de canciones del ministerio de Alabanza.
- Registros posteriores a reuniones con resumen, visibilidad (toda la iglesia,
  un ministerio o personas seleccionadas) y archivos PDF/JPG/PNG de hasta 5 MB.
- Gestión de ministerios y membresías para el rol Pastor.
- Administración de usuarios por el Pastor: crear cuentas con contraseña
  temporal, rol, estado, ministerios, restablecer o asignar contraseña (Edge
  Function `admin-users`); cada usuario puede cambiar su propia contraseña.
- Responsables asignables al crear y editables después (actividades y tareas).
- Interfaz móvil implementada con Jetpack Compose.

Las operaciones y los permisos dependen de las políticas RLS configuradas en el
proyecto Supabase. La aplicación no incluye una clave administrativa ni un modo
de datos de demostración.

## Tecnologías

- Kotlin 2.0.21 y Android Gradle Plugin 8.7.3.
- Gradle Wrapper 8.9.
- Jetpack Compose y Material 3.
- Supabase Kotlin SDK 2.6.1 (Auth, PostgREST y Storage) y Ktor Android 2.3.12.
- Android SDK: compile/target 35; mínimo Android 8.0 (API 26).

## Requisitos de desarrollo

- Android Studio con Android SDK Platform 35 y Build-Tools 35.0.0.
- JDK 17 o JDK 21 para Gradle. No uses JDK 25 con el wrapper actual.
- Un emulador Android (API 26 o posterior) o un dispositivo con depuración USB.
- Acceso de red para que Gradle descargue dependencias durante la primera
  sincronización.

## Configuración local

1. Clona este repositorio y abre su carpeta raíz en Android Studio.
2. En **Settings > Build, Execution, Deployment > Build Tools > Gradle**, elige
   JDK 17 o JDK 21 como **Gradle JDK**.
3. Instala **Android SDK Platform 35** y **Android SDK Build-Tools 35.0.0**
   desde **Tools > SDK Manager** si aún no están instalados.
4. La configuración pública de Supabase está en `supabase.properties` y se
   incluye en el repositorio para que la aplicación funcione al clonarlo.
5. En **Tools > Device Manager**, inicia un emulador o conecta un dispositivo.
6. Espera a que Android Studio complete **Gradle Sync**, selecciona la
   configuración **app** y pulsa **Run**.

También puedes compilar desde PowerShell en la raíz del repositorio:

```powershell
.\gradlew.bat assembleDebug
```

El APK de depuración se genera en
`app/build/outputs/apk/debug/app-debug.apk`.

## Configuración de Supabase

El cliente Android utiliza el mismo backend que el proyecto web. Para preparar
un proyecto Supabase nuevo, habilitar Auth y aplicar el esquema/migraciones,
sigue la sección Supabase del
[repositorio principal](https://github.com/kevin9zamora9sanche-cloud/IglesiaGestiNAdministrativa/tree/feature/iglesia-admin-ui/supabase).
Las cuentas deben ser creadas por un administrador; no hay registro público
desde la aplicación.

La URL del proyecto y la clave anon/publishable de `supabase.properties` son
configuración pública de cliente y están sujetas a las políticas RLS de
Supabase. Nunca pongas una clave `service_role` o `secret` en la aplicación ni
en el repositorio. `local.properties` y otros ajustes locales de Android Studio
no se confirman en Git.

## Estructura del proyecto

```text
.
├── app/
│   ├── src/main/           # Manifest y fuentes Kotlin
│   ├── libs/               # Dependencia JSON local usada por la app
│   └── build.gradle.kts    # Configuración de la aplicación y dependencias
├── gradle/wrapper/         # Wrapper de Gradle
├── build.gradle.kts        # Versiones de complementos
├── settings.gradle.kts     # Repositorios y módulos Gradle
└── supabase.properties.example
```

El código de la aplicación está en
`app/src/main/java/cl/iglesia/gestion/`:

- `MainActivity.kt`: sesión, navegación, permisos y orquestación de escrituras.
- `AgendaScreens.kt`: Inicio ("Mis pendientes"), Agenda general/personal y detalle.
- `ManagementScreens.kt`: Ministerios (membresías) y Proyectos.
- `MoreScreens.kt`: Alabanza (repertorio y catálogo), Personal y Avisos.
- `RecordsScreens.kt`: registros de reuniones y selección/validación de archivos.
- `Forms.kt`: formularios a pantalla completa con selectores de fecha y hora.
- `Components.kt`: componentes y formateadores compartidos.
- `ChurchData.kt`: modelos; `SupabaseBackend.kt`: acceso a Supabase;
  `Theme.kt`: paleta (contraste AA), tipografía y formas.

## Colaboración

1. Crea una rama desde la rama predeterminada del repositorio.
2. Mantén los cambios de interfaz en Compose y el acceso remoto centralizado en
   `SupabaseBackend.kt`.
3. No reduzcas las garantías de RLS ni agregues credenciales al cliente.
4. Antes de abrir un pull request, ejecuta `.\gradlew.bat assembleDebug` y
   verifica el flujo afectado en un emulador o dispositivo.
5. En la descripción del pull request, resume el cambio y los pasos de prueba.

Si una operación no tiene permisos o falla por red, la interfaz debe mostrar el
error real y no indicar que el cambio se guardó.
