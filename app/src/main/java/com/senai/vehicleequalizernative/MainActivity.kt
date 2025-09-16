package com.senai.vehicleequalizernative

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.lifecycle.lifecycleScope
import com.google.android.material.switchmaterial.SwitchMaterial
import com.senai.vehicleequalizernative.databinding.ActivityMainBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var equalizerSwitch: SwitchMaterial
    private lateinit var bassSeekBar: SeekBar
    private lateinit var midSeekBar: SeekBar
    private lateinit var trebleSeekBar: SeekBar
    private lateinit var speedLabel: TextView // Novo TextView para velocidade
    private lateinit var readSpeedButton: Button // Novo Botão para velocidade
    private lateinit var nativeStatusTextView: TextView
    private lateinit var canVolumeLabel: TextView // Novo TextView para volume CAN
    private lateinit var sendCanVolumeButton: Button // Novo Botão para volume CAN

    private val TAG = "VehicleEqualizerNative"

    private var equalizerService: IEqualizerService? = null

    // Instância do simulador de sensor de velocidade
    private val vehicleSensorSimulator = VehicleSensorSimulator("Velocidade")
    private val vehicleCanBusSimulator = VehicleCanBusSimulator() // Instância do simulador CAN
    private val activityScope = CoroutineScope(Dispatchers.Main) // Escopo para corrotinas da Activity

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            equalizerService = IEqualizerService.Stub.asInterface(service)

            Log.i(TAG, "Conectado ao EqualizerService.")

            equalizerService?.setEqualizerEnabled(equalizerSwitch.isChecked)
            equalizerService?.setBassLevel(bassSeekBar.progress)
            equalizerService?.setMidLevel(midSeekBar.progress)
            equalizerService?.setTrebleLevel(trebleSeekBar.progress)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            equalizerService = null
            Log.w(TAG, "Desconectado do EqualizerService.")
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Inicializa as variáveis conectando-as aos IDs do layout XML
        equalizerSwitch = findViewById(R.id.equalizerSwitch)
        bassSeekBar = findViewById(R.id.bassSeekBar)
        midSeekBar = findViewById(R.id.midSeekBar)
        trebleSeekBar = findViewById(R.id.trebleSeekBar)
        nativeStatusTextView = findViewById(R.id.nativeStatusTextView) // Assumindo que você tem um TextView com este ID
        speedLabel = findViewById(R.id.speedLabel) // Inicializa o TextView
        readSpeedButton = findViewById(R.id.readSpeedButton) // Inicializa o Botão
        canVolumeLabel = findViewById(R.id.canVolumeLabel) // Inicializa o TextView CAN
        sendCanVolumeButton = findViewById(R.id.sendCanVolumeButton) // Inicializa o Botão CAN

        // Exibe o texto da função nativa de exemplo
        nativeStatusTextView.text = stringFromJNI()

        // Define o estado inicial dos SeekBars com base no estado do Switch
        setEqualizerControlsEnabled(equalizerSwitch.isChecked)

        // Listener para o Switch do equalizador
        equalizerSwitch.setOnCheckedChangeListener { _, isChecked ->
            setEqualizerControlsEnabled(isChecked)
            equalizerService?.setEqualizerEnabled(isChecked)
            setEqualizerEnabledNative(isChecked) // Chama a função nativa
        }

        // Listener genérico para os SeekBars
        val seekBarChangeListener = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                // Opcional: exibir valor em tempo real na UI
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                when (seekBar?.id) {
                    R.id.bassSeekBar -> {
                        equalizerService?.setBassLevel(seekBar.progress)
                        setBassLevelNative(seekBar.progress) // Chama a função nativa
                    }

                    R.id.midSeekBar -> {
                        equalizerService?.setMidLevel(seekBar.progress)
                        setMidLevelNative(seekBar.progress) // Chama a função nativa do Módulo 04
                    }

                    R.id.trebleSeekBar -> {
                        equalizerService?.setTrebleLevel(seekBar.progress)
                        setTrebleLevelNative(seekBar.progress) // Chama a função nativa do Módulo
                    }
                }
            }
        }

        // Atribui o listener a cada SeekBar
        bassSeekBar.setOnSeekBarChangeListener(seekBarChangeListener)
        midSeekBar.setOnSeekBarChangeListener(seekBarChangeListener)
        trebleSeekBar.setOnSeekBarChangeListener(seekBarChangeListener)

        // Listener para o botão de leitura de velocidade
        readSpeedButton.setOnClickListener {
            giveHapticFeedback() // <<< ADICIONADO
            val currentSpeed = vehicleSensorSimulator.readSensorData()
            speedLabel.text = "Velocidade Atual: $currentSpeed km/h"
            Log.d(TAG, "Velocidade lida: $currentSpeed km/h")
        }

        // Listener para o botão de envio de volume CAN
        sendCanVolumeButton.setOnClickListener {
            giveHapticFeedback()
            // Simula o envio de uma mensagem CAN de volume (ID 0x123, valor aleatório 0-100)
            val randomVolume = (0..100).random()
            val message = CanMessage(id = 0x123, data = byteArrayOf(randomVolume.toByte()))
            vehicleCanBusSimulator.sendMessage(message)
        }

        // Coleta mensagens CAN recebidas e atualiza a UI
        /*activityScope.launch {
            vehicleCanBusSimulator.canMessageFlow.collect { message ->
                if (message.id == 0x123 && message.data.isNotEmpty()) {
                    val volume = message.data[0].toInt() and 0xFF
                    canVolumeLabel.text = "Volume CAN: $volume"
                    // Em um cenário real, você poderia usar este volume para ajustar o áudio
                    // equalizerService?.setMasterVolume(volume) // Exemplo de chamada ao serviço
                }
            }
        }*/

        lifecycleScope.launch {
            vehicleCanBusSimulator.canMessageFlow.collect { message ->
                if (message.id == 0x123 && message.data.isNotEmpty()) {
                    val volume = message.data[0].toInt() and 0xFF
                    canVolumeLabel.text = "Volume CAN: $volume"
                    // Em um cenário real, você poderia usar este volume para ajustar o áudio
                    // equalizerService?.setMasterVolume(volume) // Exemplo de chamada ao serviço
                }
            }
        }



    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun giveHapticFeedback() {
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (vibrator.hasVibrator()) {
            vibrator.vibrate(
                VibrationEffect.createOneShot(
                    30, // duração em ms
                    VibrationEffect.DEFAULT_AMPLITUDE
                )
            )
        }
    }


    override fun onStart() {
        super.onStart()
        val intent = Intent(this, EqualizerService::class.java)
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    override fun onStop() {
        super.onStop()
        if (equalizerService != null) {
            unbindService(serviceConnection)
            equalizerService = null
            Log.i(TAG, "Desvinculado do EqualizerService.")
        }

        // Importante: parar o simulador CAN quando a atividade for destruída ou não for mais necessária
        vehicleCanBusSimulator.stopSimulator()
        activityScope.cancel() // Cancela as corrotinas do escopo da atividade
    }

    override fun onDestroy() {
        super.onDestroy()
        // TODO: liberar buffers nativos (JNI) se forem alocados no futuro
        vehicleCanBusSimulator.stopSimulator()
        Log.i(TAG, "MainActivity destruída e recursos liberados.")
    }

    /**
     * Habilita ou desabilita os controles do equalizador (SeekBars).
     * @param enabled true para habilitar, false para desabilitar.
     */
    private fun setEqualizerControlsEnabled(enabled: Boolean) {
        bassSeekBar.isEnabled = enabled
        midSeekBar.isEnabled = enabled
        trebleSeekBar.isEnabled = enabled
    }

    /**
     * Declaração dos métodos nativos que serão implementados pela
    biblioteca C++.
     * A palavra-chave 'external' indica que a implementação está em
    código nativo.
     */
    external fun stringFromJNI(): String
    external fun setEqualizerEnabledNative(enabled: Boolean)
    external fun setBassLevelNative(level: Int)
    external fun setMidLevelNative(level: Int)
    external fun setTrebleLevelNative(level: Int)

    companion object {
    // Usado para carregar a biblioteca nativa 'vehicleequalizernative' na inicialização do aplicativo.
    // O nome da biblioteca deve corresponder ao 'target_link_libraries' no CMakeLists.txt.
        init {
            System.loadLibrary("vehicleequalizernative")
        }
    }


}

