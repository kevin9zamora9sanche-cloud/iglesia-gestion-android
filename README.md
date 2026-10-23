# Iglesia Gestión — Android

Aplicación Android para la gestión del Centro Mundial de Adoración y Alabanza:
agenda, actividades, tareas, proyectos, ministerios, alabanza, registros de
reuniones y administración de usuarios. Está hecha en **Kotlin + Jetpack
Compose** y usa **Supabase** (autenticación, base de datos y archivos).

> **¿Solo quieres ejecutar la app?** Sigue la [Guía rápida](#guía-rápida).
> El backend ya está configurado: no necesitas tocar Supabase.

---

## Índice

1. [Guía rápida](#guía-rápida)
2. [Requisitos](#requisitos)
3. [Instalación paso a paso](#instalación-paso-a-paso)
4. [Cuentas de acceso](#cuentas-de-acceso)
5. [Solución de problemas](#solución-de-problemas)
6. [Backend (solo administradores)](#backend-solo-administradores)
7. [Estructura del proyecto](#estructura-del-proyecto)
8. [Cómo colaborar](#cómo-colaborar)
9. [Funcionalidad](#funcionalidad)

---

## Guía rápida

```powershell
git clone https://github.com/kevin9zamora9sanche-cloud/iglesia-gestion-android.git
```

1. Abre la carpeta clonada en **Android Studio** (*File › Open*).
2. Elige **JDK 17 o 21** como *Gradle JDK* (ver [paso 3](#3-configura-el-jdk-de-gradle)).
3. Espera a que termine **Gradle Sync**.
4. Inicia un emulador o conecta tu teléfono y pulsa **Run ▶**.
5. Ingresa con la cuenta que te entregue el Pastor/Administrador.

No hay que crear ni editar archivos de configuración: `supabase.properties`
ya viene en el repositorio con los datos públicos del proyecto.

---

## Requisitos

| Herramienta | Versión | Notas |
|---|---|---|
| Android Studio | Ladybug (2024.2) o posterior | Incluye el SDK Manager y el emulador |
| JDK para Gradle | **17 o 21** | ⚠️ JDK 25 **no** funciona con este Gradle |
| Android SDK Platform | 35 | Se instala desde el SDK Manager |
| Android SDK Build-Tools | 35.0.0 | Se instala desde el SDK Manager |
| Dispositivo | Android 8.0 (API 26) o superior | Emulador o teléfono con depuración USB |
| Internet | — | Gradle descarga dependencias la primera vez y la app usa Supabase |

---

## Instalación paso a paso

### 1. Clona el repositorio

```powershell
git clone https://github.com/kevin9zamora9sanche-cloud/iglesia-gestion-android.git
cd iglesia-gestion-android
```

### 2. Ábrelo en Android Studio

*File › Open* y selecciona la carpeta `iglesia-gestion-android` (la raíz,
donde está `settings.gradle.kts`). Android Studio crea `local.properties` con
la ruta de tu SDK automáticamente; ese archivo es personal y no se sube a Git.

### 3. Configura el JDK de Gradle

*Settings › Build, Execution, Deployment › Build Tools › Gradle* →
**Gradle JDK**: elige **17** o **21** (por ejemplo, *jbr-21*, el que trae
Android Studio).

### 4. Instala el SDK necesario

*Tools › SDK Manager*:

- Pestaña **SDK Platforms**: marca **Android 15 (API 35)**.
- Pestaña **SDK Tools**: marca **Android SDK Build-Tools 35**.

### 5. Sincroniza

Pulsa **Sync Project with Gradle Files** (ícono del elefante) y espera a que
termine sin errores. La primera vez tarda varios minutos.

### 6. Prepara un dispositivo

- **Emulador:** *Tools › Device Manager › Create Device*, elige un Pixel con
  una imagen de sistema API 26 o superior e inícialo.
- **Teléfono:** activa *Opciones de desarrollador › Depuración USB* y conéctalo.

### 7. Ejecuta

Selecciona la configuración **app** y tu dispositivo, y pulsa **Run ▶**.

### Alternativa: compilar desde la terminal

```powershell
.\gradlew.bat assembleDebug          # Windows
./gradlew assembleDebug              # macOS / Linux
```

El APK queda en `app/build/outputs/apk/debug/app-debug.apk` y se instala con:

```powershell
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## Cuentas de acceso

- **No hay registro público ni cuentas de prueba.** Cada persona usa su propia
  cuenta.
- El **Pastor/Administrador** crea las cuentas desde la app:
  *Más › Usuarios › Nueva cuenta*. Ahí define el correo, el rol, los
  ministerios y una contraseña temporal.
- Al ingresar por primera vez, cambia tu contraseña en
  *Más › Cambiar mi contraseña*.
- Si olvidas la contraseña, pide al Pastor que te asigne una temporal o usa
  *¿Olvidaste tu contraseña?* en la pantalla de inicio de sesión.

Roles disponibles:

| Rol | Puede |
|---|---|
| Pastor/Administrador | Todo: usuarios, ministerios, proyectos, actividades, registros de reuniones |
| Líder de ministerio | Gestionar actividades, tareas y reuniones de los ministerios que lidera |
| Líder normal | Ver lo que le corresponde y actualizar el estado de sus tareas |

Los permisos se aplican en el servidor (políticas RLS de Supabase): ocultar un
botón en la app no es lo que protege los datos.

---

## Solución de problemas

| Síntoma | Causa probable | Solución |
|---|---|---|
| Gradle falla con *Unsupported class file major version* | Gradle usa JDK 25 | Cambia el **Gradle JDK** a 17 o 21 ([paso 3](#3-configura-el-jdk-de-gradle)) |
| *SDK location not found* | Falta `local.properties` | Abre el proyecto con Android Studio para que lo cree, o crea `local.properties` con `sdk.dir=RUTA_A_TU_SDK` |
| *Failed to find Build Tools 35* | Falta el SDK | Instálalo desde el SDK Manager ([paso 4](#4-instala-el-sdk-necesario)) |
| El login dice *"Correo o contraseña incorrectos, o no hay conexión"* con datos correctos | El dispositivo no tiene internet, o el proyecto Supabase está **pausado** (pasa tras ~7 días sin uso en el plan gratuito) | Revisa la conexión; un administrador debe reactivarlo en el panel de Supabase (*Restore project*) |
| Después de ingresar aparece *"No se pudieron cargar los datos"* | Faltan migraciones en la base de datos | Un administrador debe aplicarlas ([Backend](#backend-solo-administradores)) |
| *"Registros… aún no están habilitados en el servidor"* | Falta la migración de registros de reuniones | Igual que el anterior |
| Crear una cuenta falla con *"No se pudo contactar el servicio de cuentas"* | La función `admin-users` no está desplegada | Un administrador debe desplegarla ([Backend](#backend-solo-administradores)) |
| `adb: more than one device/emulator` | Hay varios dispositivos conectados | Usa `adb -s emulator-5554 …` (mira la lista con `adb devices`) |

---

## Backend (solo administradores)

El backend es un proyecto Supabase ya enlazado en `supabase/config.toml`
(`sbvgcnahuunjnczecmpt`). **Quien solo desarrolla la app no necesita esta
sección.** Los detalles de cada migración están en
[`supabase/README.md`](supabase/README.md).

### Aplicar migraciones

Requiere Node.js 20+ y acceso al proyecto en Supabase.

```powershell
npx.cmd --yes supabase login
npx.cmd --yes supabase link --project-ref sbvgcnahuunjnczecmpt
npx.cmd --yes supabase migration list --linked        # ver cuáles faltan
npx.cmd --yes supabase db push --linked --dry-run     # revisar el plan
npx.cmd --yes supabase db push --linked               # aplicar
```

Las migraciones están en `supabase/migrations/` y se aplican en orden de fecha.
Están escritas para poder re-ejecutarse sobre un esquema existente.

### Desplegar la función de administración de cuentas

```powershell
npx.cmd --yes supabase functions deploy admin-users --project-ref sbvgcnahuunjnczecmpt --use-api
```

Crear cuentas requiere la clave `service_role`, que **nunca** va en la app. La
función `supabase/functions/admin-users` verifica que quien llama sea un
Pastor activo y solo entonces usa esa clave en el servidor.

### Designar el primer Pastor

Si la base está vacía, crea la cuenta en *Authentication › Users* y ejecuta en
el *SQL Editor*:

```sql
update public.profiles set role = 'pastor'
where id = (select id from auth.users where email = 'correo@ejemplo.com');
```

Desde ese momento, el Pastor administra el resto de las cuentas desde la app.

### Seguridad

- `supabase.properties` contiene solo la URL y la clave **pública**
  (anon/publishable), protegidas por RLS. Por eso está en el repositorio.
- Nunca subas una clave `service_role`/`secret`, contraseñas ni tokens.

---

## Estructura del proyecto

```text
.
├── app/
│   ├── build.gradle.kts            # Dependencias y configuración de la app
│   ├── libs/                       # Dependencia JSON local
│   └── src/main/java/cl/iglesia/gestion/
│       ├── MainActivity.kt         # Sesión, navegación, permisos y escrituras
│       ├── AgendaScreens.kt        # Inicio ("Mis pendientes"), Agenda y detalle
│       ├── ManagementScreens.kt    # Ministerios y Proyectos (barra de avance)
│       ├── MoreScreens.kt          # Alabanza, Usuarios y Avisos
│       ├── RecordsScreens.kt       # Registros de reuniones y archivos
│       ├── Forms.kt                # Formularios (crear/editar, responsables)
│       ├── Components.kt           # Componentes y formatos compartidos
│       ├── ChurchData.kt           # Modelos de datos
│       ├── SupabaseBackend.kt      # Único punto de acceso a Supabase
│       └── Theme.kt                # Colores, tipografía y formas
├── supabase/
│   ├── migrations/                 # Esquema y políticas RLS (SQL)
│   ├── functions/admin-users/      # Edge Function de administración de cuentas
│   ├── config.toml                 # Enlace con el proyecto Supabase
│   └── README.md                   # Detalle de cada migración
├── supabase.properties             # URL y clave pública de Supabase
└── supabase.properties.example     # Plantilla para usar otro proyecto
```

Para apuntar la app a **otro** proyecto Supabase, reemplaza los valores de
`supabase.properties` (usa `supabase.properties.example` como guía) y vuelve a
sincronizar Gradle.

---

## Cómo colaborar

1. Actualiza tu copia y crea una rama desde `main`:
   ```powershell
   git checkout main
   git pull
   git checkout -b feature/descripcion-corta
   ```
2. Mantén la interfaz en Compose y todo acceso remoto en `SupabaseBackend.kt`.
3. Si cambias la base de datos, agrega una **nueva** migración en
   `supabase/migrations/` (no edites las ya aplicadas) y documéntala en
   `supabase/README.md`.
4. Antes de subir, compila (`.\gradlew.bat assembleDebug`) y prueba el flujo en
   un emulador o teléfono.
5. Escribe los commits **en español** con el formato
   `tipo: descripción en minúsculas`, por ejemplo:
   - `feat: agregar filtro por ministerio en la agenda`
   - `fix: corregir el cálculo del avance del proyecto`
   - `docs: actualizar la guía de instalación`
   - `style:`, `refactor:`, `chore:` según corresponda.
6. Sube tu rama y abre un *pull request* hacia `main` explicando el cambio y
   cómo probarlo.

Reglas del proyecto:

- Si una operación falla (permisos o red), la app debe mostrar el error y
  **nunca** indicar que se guardó.
- No debilites las políticas RLS ni agregues credenciales al cliente.
- Los requerimientos funcionales están en
  [`CONTRIBUTING.md`](CONTRIBUTING.md).

---

## Funcionalidad

- **Inicio "Mis pendientes":** tareas y actividades propias con fecha y avisos sin leer.
- **Agenda** general y personal, con semana navegable, filtros por tipo
  (Culto, Ensayo, Aseo, Reunión de líderes, Libre, Tareas, Reuniones) y ficha
  de detalle con responsables, invitados, predicador, director y tareas.
- **Actividades** con tipo, clasificación (ministerio, proyecto, general o
  individual), responsables y estado (Por hacer, En curso, En pausa, Completada).
- **Tareas** hijas de una actividad, con responsable, prioridad, fecha límite y
  comentarios. Los responsables se pueden cambiar después.
- **Proyectos** institucionales con responsable, fechas, presupuesto y **barra
  de avance** calculada automáticamente a partir de sus actividades y tareas.
- **Alabanza:** repertorio de cada culto (orden, tono, quién dirige) y catálogo
  de canciones con título, artista, tonalidad, enlace a la versión de referencia
  y archivos (partitura, letra o acordes en PDF/JPG/PNG de hasta 5 MB). El Pastor
  y los líderes de Alabanza registran, editan y eliminan canciones y archivos;
  todos pueden verlos. Una canción usada en algún repertorio no se puede eliminar.
- **Registros de reuniones** con resumen, visibilidad y archivos PDF/JPG/PNG de
  hasta 5 MB.
- **Ministerios (Pastor):** crear, editar, desactivar y reactivar. Un ministerio
  desactivado se oculta al resto, pero conserva integrantes y registros.
- **Usuarios (Pastor):** crear cuentas, roles, ministerios, activar o desactivar
  y restablecer contraseñas. Al crear una cuenta se muestran el correo y la
  contraseña temporal para entregarlos en persona. Cada usuario puede cambiar la suya.
- **Accesibilidad:** letra ampliada (texto principal de 18 sp) y botones grandes,
  pensados para personas mayores.
- **Avisos** dentro de la app para asignaciones, cambios de agenda, repertorios,
  reuniones y proyectos.

**Tecnologías:** Kotlin 2.0.21 · Android Gradle Plugin 8.7.3 · Gradle 8.9 ·
Jetpack Compose + Material 3 · Supabase Kotlin SDK 2.6.1 (Auth, PostgREST,
Storage, Functions) · compileSdk/targetSdk 35 · minSdk 26.
