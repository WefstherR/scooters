# Room y DataStore incluyen sus propias reglas de consumo.
# Mantener nombres de enums persistidos (se guardan por nombre en la base de datos).
-keepclassmembers enum cl.recibos.app.domain.model.** { *; }
