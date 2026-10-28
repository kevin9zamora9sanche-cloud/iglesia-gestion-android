package cl.iglesia.gestion

import kotlin.math.roundToInt

/**
 * Avance de un proyecto calculado en el cliente (staging no guarda una columna de avance).
 * Una actividad sin tareas vale 100 % solo si está completada; con tareas, su fracción
 * completada. El proyecto promedia sus actividades y vale 0 cuando no tiene ninguna.
 * Los estados son los internos de la app (pending, in_progress, paused, completed).
 */
object ProjectProgress {
    class Item(val activityStatus: String, val taskStatuses: List<String>)

    fun percent(items: List<Item>): Int {
        if (items.isEmpty()) return 0
        return items.map { item ->
            if (item.taskStatuses.isEmpty()) {
                if (item.activityStatus == "completed") 100.0 else 0.0
            } else {
                item.taskStatuses.count { it == "completed" } * 100.0 / item.taskStatuses.size
            }
        }.average().roundToInt().coerceIn(0, 100)
    }
}
