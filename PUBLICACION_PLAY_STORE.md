# Publicación en Google Play: cumplimiento legal

Guía para publicar Gestión Iglesia (`cl.iglesia.gestion`) cumpliendo las
políticas de Google Play y la ley chilena de datos personales (Ley 19.628 y
Ley 21.719).

## 1. Antes de compilar el AAB

1. Reemplaza los valores marcados `REEMPLAZAR` (correo de privacidad y
   dirección) en:
   - `app/src/main/java/cl/iglesia/gestion/LegalScreens.kt` (`LegalInfo`)
   - `docs/privacidad.html`, `docs/terminos.html`, `docs/eliminar-cuenta.html`
     y `docs/index.html`
   Busca los pendientes con `git grep REEMPLAZAR`.
2. Usa un correo que alguien revise: por él llegan las solicitudes de
   eliminación y de derechos.
3. Activa GitHub Pages: Settings > Pages > Deploy from a branch > `main` /
   `/docs`. Comprueba que abran estas tres URL:
   - https://kevin9zamora9sanche-cloud.github.io/iglesia-gestion-android/privacidad.html
   - https://kevin9zamora9sanche-cloud.github.io/iglesia-gestion-android/terminos.html
   - https://kevin9zamora9sanche-cloud.github.io/iglesia-gestion-android/eliminar-cuenta.html
4. Si cambias el texto de las políticas, cambia también
   `LegalInfo.POLICY_VERSION`. Así la app vuelve a pedir el consentimiento.

## 2. Play Console

### Contenido de la app

| Sección | Respuesta |
| --- | --- |
| Política de privacidad | URL de `privacidad.html` |
| Acceso a la app | Toda la app requiere iniciar sesión. Crea una cuenta de prueba con rol de líder y entrega a Google el correo y la contraseña. |
| Anuncios | No contiene anuncios |
| Clasificación de contenido | Categoría "Utilidad, productividad, comunicación u otra". Sin violencia, sexo, drogas, apuestas ni compras. El contenido generado por usuarios solo circula entre cuentas internas. |
| Público objetivo | Solo 18 años o más. No marques edades menores: así la app queda fuera del programa Familias y no requiere consentimiento parental. |
| Apps de noticias, gubernamentales, de salud o de préstamos | No |
| Eliminación de datos | Sí, la app tiene cuentas. URL: `eliminar-cuenta.html`. |

### Seguridad de los datos (Data safety)

- ¿Recopila o comparte datos? **Recopila: sí. Comparte: no.** Supabase es un
  proveedor de servicios, por lo que no cuenta como "compartir".
- ¿Cifra los datos en tránsito? **Sí** (HTTPS).
- ¿El usuario puede pedir que se borren sus datos? **Sí**.

| Tipo de dato | Recopilado | Opcional | Finalidad |
| --- | --- | --- | --- |
| Información personal > Nombre | Sí | No | Funcionalidad de la app, administración de la cuenta |
| Información personal > Correo electrónico | Sí | No | Funcionalidad de la app, administración de la cuenta |
| Información personal > Número de teléfono | Sí | Sí | Funcionalidad de la app |
| Información personal > Creencias religiosas | Sí | No | Funcionalidad de la app (pertenencia a la iglesia y a sus ministerios) |
| Información personal > Otra información (rol) | Sí | No | Funcionalidad de la app |
| Archivos y documentos | Sí | Sí | Funcionalidad de la app (adjuntos de actas y canciones) |
| Mensajes > Otros mensajes en la app (comentarios y avisos) | Sí | Sí | Funcionalidad de la app |
| Actividad en la app > Otras acciones (registro de auditoría) | Sí | No | Seguridad y prevención de fraudes |

No declares ubicación, contactos, fotos, audio, salud, finanzas, navegación,
identificadores de dispositivo ni diagnósticos: la app no los recopila.

### Ficha de la tienda

- Describe solo funciones que existen. No uses afirmaciones sin respaldo, como
  "la más segura", "100 % privada" o "n.º 1".
- No publiques reseñas, valoraciones ni testimonios inventados, y no ofrezcas
  nada a cambio de reseñas.
- Indica "Gratis": la app no tiene compras ni suscripciones.
- Usa capturas reales de la app con datos de prueba, sin nombres reales de
  miembros.
- Usa solo íconos y gráficos propios o con licencia comercial.
- Categoría sugerida: Productividad.
- Datos del desarrollador: Play muestra públicamente el nombre y el correo, y
  también la dirección en cuentas de organización. Usa los datos de la
  iglesia, no un correo personal.

## 3. Los 20 puntos

| # | Punto | ¿Aplica? | Dónde quedó |
| --- | --- | --- | --- |
| 1 | Política de privacidad | Sí | `docs/privacidad.html` y en la app (inicio de sesión y Más > Privacidad y datos) |
| 2 | Términos y condiciones | Sí | `docs/terminos.html` y en la app |
| 3 | Política de reembolsos | No, la app es gratuita | Los términos declaran que no hay cobros ni reembolsos |
| 4 | Política de cookies | No: la app es nativa, sin WebView ni cookies | La política declara que no hay cookies. GitHub Pages no usa analítica. |
| 5 | Banner de cookies | No | No hay cookies que consentir |
| 6 | Consentimiento en formularios | Sí | `ConsentScreen`: tres casillas desmarcadas (términos, dato sensible religioso y mayoría de edad) antes de entrar |
| 7 | Evitar datos innecesarios | Sí | Solo pide el permiso `INTERNET`. El teléfono es "(opcional)" y no hay respaldo en la nube. |
| 8 | Auditar SDKs de terceros | Sí | Compose/AndroidX, Ktor, supabase-kt y org.json. Ninguno incluye publicidad ni analítica. |
| 9 | Patrones oscuros | Sí | Casillas desmarcadas, "No acepto" tan visible como aceptar y eliminación de cuenta accesible |
| 10 | Cobros ocultos | No hay cobros | Declarado en los términos y en la ficha |
| 11 | Reseñas falsas | Aplica a la ficha | Ver "Ficha de la tienda" |
| 12 | Afirmaciones sin respaldo | Aplica a la ficha | Textos de la app revisados |
| 13 | Texto alternativo | Sí | Los íconos con acción ya tenían `contentDescription`. Se añadió al avatar del menú de cuenta y se ocultaron al lector los símbolos decorativos. |
| 14 | Corrección de colores | Sí | `TextMuted` pasó a `#5B6B80`: ahora cumple 4.5:1 sobre todos los fondos |
| 15 | Navegación con teclado | Sí | En Compose los controles son enfocables. Las casillas usan `toggleable` con rol Checkbox. |
| 16 | Datos del negocio | Sí | `LegalInfo` y Más > Privacidad y datos. Faltan los valores `REEMPLAZAR`. |
| 17 | Consentimiento parental | Evitado | App solo para mayores de 18 (casilla y público objetivo 18+) |
| 18 | Link de baja | No: no hay correos de marketing | Los avisos solo aparecen dentro de la app |
| 19 | Licencia de imágenes | Sí | Ícono propio y Más > Privacidad y datos > Licencias. Los términos exigen derechos sobre los archivos subidos. |
| 20 | Eliminación de datos | Sí | Botón en la app con correo prellenado y `docs/eliminar-cuenta.html` |

## 4. Procesar una solicitud de eliminación

No borres el usuario desde Authentication > Users. Varias tablas
(`activities`, `tasks`, `meeting_records` y otras) referencian
`profiles(id)` sin cascada, así que el borrado falla. En su lugar, anonimiza la
cuenta.

1. Verifica que la solicitud venga del correo de la cuenta.
2. En el SQL Editor de Supabase, ejecuta lo siguiente con el ID de la
   solicitud:

   ```sql
   begin;
   -- Quita los datos personales; el contenido de la iglesia queda sin autor identificable.
   update public.profiles
     set full_name = 'Usuario eliminado', phone = null, is_active = false
     where id = 'ID_DEL_USUARIO';
   delete from public.ministry_memberships where user_id = 'ID_DEL_USUARIO';
   delete from public.notifications where user_id = 'ID_DEL_USUARIO';
   -- Impide el acceso y borra el correo real.
   update auth.users
     set email = 'eliminado-' || id || '@invalid.local',
         encrypted_password = '',
         phone = null,
         raw_user_meta_data = '{}'::jsonb,
         banned_until = 'infinity'
     where id = 'ID_DEL_USUARIO';
   delete from auth.identities where user_id = 'ID_DEL_USUARIO';
   delete from auth.sessions where user_id = 'ID_DEL_USUARIO';
   commit;
   ```

3. Borra de Storage los archivos personales que la persona pida eliminar.
4. Confirma la eliminación por correo dentro de 30 días.

## 5. Límites conocidos

- El consentimiento se guarda en el dispositivo (SharedPreferences), así que se
  vuelve a pedir al reinstalar la app. Para tener constancia en el servidor,
  agrega una tabla `consents` con `user_id`, `policy_version` y `accepted_at`.
- La administración ejecuta la eliminación de forma manual. Google Play acepta
  que la app solo permita iniciar la solicitud.
- Este documento no reemplaza la revisión de un abogado.

## 6. Generar el bundle firmado

1. Crea la llave de subida una sola vez (pide contraseñas y datos):

   ```sh
   keytool -genkeypair -v -keystore upload-keystore.jks -alias upload -keyalg RSA -keysize 2048 -validity 10000
   ```

2. Copia `keystore.properties.example` como `keystore.properties` y completa
   las contraseñas. Ambos archivos están en `.gitignore`.
3. Respalda `upload-keystore.jks` y sus contraseñas fuera del repositorio. Si se
   pierden, hay que pedir a Google un restablecimiento de la llave de subida.
4. Genera el bundle con `gradlew.bat :app:bundleRelease`. El archivo queda en
   `app/build/outputs/bundle/release/app-release.aab`.
5. En Play Console, activa la firma de apps de Google Play y sube el `.aab`
   primero a "Prueba interna".
6. Cada nueva subida necesita un `versionCode` mayor en `app/build.gradle.kts`.
