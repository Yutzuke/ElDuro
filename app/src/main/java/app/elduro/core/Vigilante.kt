package app.elduro.core

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import app.elduro.R
import app.elduro.data.ElDuroDb
import app.elduro.data.Sentido
import app.elduro.ui.MainActivity
import java.time.YearMonth
import java.util.concurrent.TimeUnit

/**
 * Trabajo periódico (cada ~6 h): tasas, recurrentes automáticos, ZIP diario,
 * y avisos de cuotas por vencer, recurrentes pendientes y presupuestos al 80 %/100 %.
 */
class Vigilante(ctx: Context, p: WorkerParameters) : Worker(ctx, p) {

    override fun doWork(): Result {
        val app = applicationContext
        val db = ElDuroDb.get(app)
        if (System.currentTimeMillis() - Prefs.ultimaConsulta(app) > 3 * 3600_000L)
            runCatching { if (ConsultaTasas.actualizar(db).ok.isNotEmpty()) Prefs.setUltimaConsulta(app, System.currentTimeMillis()) }
        val pendientes = runCatching { Control.procesarRecurrentes(db) }.getOrDefault(emptyList())
        runCatching { CarpetaSync.sincronizar(app) }
        runCatching { Exportar.zipSiToca(app) }
        runCatching { avisar(app, db, pendientes.size) }
        return Result.success()
    }

    private fun avisar(app: Context, db: ElDuroDb, pendientes: Int) {
        val sp = Prefs.sp(app)
        val hoy = Fechas.hoy()
        val lineas = mutableListOf<String>()
        // cuotas que vencen en ≤ 2 días (un aviso por vencimiento)
        for (e in Control.estados(db.deudas().all().filter { !it.cerrada && it.sentido == Sentido.DEBO }, db.movimientos().all())) {
            val dias = e.diasParaVencer() ?: continue
            val clave = "av_${e.deuda.uid}_${e.proximoVence}"
            if (dias <= 2 && !sp.getBoolean(clave, false)) {
                lineas += "${e.deuda.nombre}: cuota de ${Dinero.fmt(e.cuota ?: e.saldo, e.deuda.moneda)} " +
                        when { dias < 0 -> "VENCIDA hace ${-dias} d"; dias == 0L -> "vence HOY"; else -> "vence en $dias d" }
                sp.edit().putBoolean(clave, true).apply()
            }
        }
        if (pendientes > 0 && sp.getString("av_rec", null) != hoy) {
            lineas += "$pendientes movimiento(s) recurrente(s) por confirmar"; sp.edit().putString("av_rec", hoy).apply()
        }
        // presupuestos
        val carteras = db.carteras().all().associateBy { it.id }
        val tasas = TablaTasas(db.tasas().all())
        val mes = YearMonth.now()
        for (libro in Libros.activos(app, db.carteras().all())) {
            val movs = Finanzas.delLibro(db.movimientos().all(), carteras, libro)
            for (a in Control.avances(db.presupuestos().all(), movs, db.deudas().all(), carteras, libro, mes, tasas)) {
                val nivel = when { a.fraccion >= 1 -> 100; a.fraccion >= 0.8 -> 80; else -> continue }
                val clave = "av_pres_${libro}_${a.p.categoria}_${mes}_$nivel"
                if (!sp.getBoolean(clave, false)) {
                    lineas += "$libro · ${a.p.categoria}: ${(a.fraccion * 100).toInt()}% del presupuesto"
                    sp.edit().putBoolean(clave, true).apply()
                }
            }
        }
        if (lineas.isNotEmpty()) notificar(app, if (lineas.size == 1) lineas.first() else "${lineas.size} avisos de ElDuro", lineas)
    }

    companion object {
        private const val CANAL = "avisos"
        private const val TRABAJO = "elduro-vigilante"

        fun programar(ctx: Context) {
            val req = PeriodicWorkRequestBuilder<Vigilante>(6, TimeUnit.HOURS).build()
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(TRABAJO, ExistingPeriodicWorkPolicy.KEEP, req)
        }

        fun notificar(ctx: Context, titulo: String, lineas: List<String>) {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CANAL, "Cuotas, presupuestos y recurrentes", NotificationManager.IMPORTANCE_DEFAULT))
            if (ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
            val pi = PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
            val n = NotificationCompat.Builder(ctx, CANAL).setSmallIcon(R.drawable.ic_noti).setContentTitle(titulo)
                .setContentText(lineas.joinToString(" · ")).setStyle(NotificationCompat.InboxStyle().also { s -> lineas.forEach { s.addLine(it) } })
                .setContentIntent(pi).setAutoCancel(true).build()
            NotificationManagerCompat.from(ctx).notify(1, n)
        }
    }
}
