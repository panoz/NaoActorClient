package robots.naoactorclient

import android.app.Activity
import android.app.Dialog
import android.os.Bundle
import akka.actor.Props
import robots.common.RobotRequest
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.content.Context
import android.content.DialogInterface
import android.graphics.Canvas
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.ScheduledFuture
import android.graphics.Paint
import android.graphics.RectF
import android.widget.FrameLayout
import android.widget.RelativeLayout
import scala.util.Success
import akka.actor.Actor
import android.os.Handler
import android.os.Message
import scala.util.Try
import android.os.Looper
import android.hardware.SensorManager
import android.hardware.Sensor
import android.hardware.SensorEventListener
import android.hardware.SensorEvent
import android.widget.Button
import android.view.MotionEvent
import android.view.MotionEvent
import java.io.ByteArrayInputStream
import android.graphics.drawable.BitmapDrawable
import android.graphics.BitmapFactory
import android.widget.ImageView
import android.graphics.drawable.ShapeDrawable
import android.view.ViewTreeObserver
import android.view.ViewTreeObserver.OnGlobalLayoutListener
import android.widget.ToggleButton
import android.view.View.OnClickListener
import scala.ref.WeakReference
import scala.collection.JavaConversions._
import android.os.Messenger
import android.content.SharedPreferences
import org.apache.http.conn.util.InetAddressUtils
import android.widget.Toast
import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory
import android.os.AsyncTask
import scala.util.Failure
import akka.actor.ActorRef
import akka.remote.RemoteLifeCycleEvent
import akka.util.Timeout
import akka.pattern.ask
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import DeviceMode._
import Math.PI
import android.graphics.Bitmap
import android.view.GestureDetector
import android.support.v4.view.GestureDetectorCompat
import UtilsScala.extractAndExecute
import Config._
import Config.Keys._
import android.content.res.Configuration
import java.util.concurrent.ScheduledExecutorService
import android.view.ViewGroup

class MainActivity extends Activity {

  val TAG = "MainActivity"

  val languages_group = R.id.languages_group
  private var prefs: SharedPreferences = null
  private var toast: Toast = null

  private var mainFrame: RelativeLayout = null
  private var shapesLayout: RelativeLayout = null
  private var walkImageView: ImageView = null
  private var rotateImageView: ImageView = null

  private val viewWidth = collection.mutable.Map[View, Float]()
  private val viewHeight = collection.mutable.Map[View, Float]()

  private var textToSpeechLanguages = Array[String]()
  private var textToSpeechLanguage = ""
  private var language2idMap = collection.mutable.Map[String, Int]()

  private var actorSystem: ActorSystem = null
  private var actor: ActorRef = null

  private var mDetector: GestureDetectorCompat = null

  private var headStiffnessButton: ToggleButton = null

  private var _headStiffness = false
  private def headStiffness = _headStiffness
  private def headStiffness_=(b: Boolean) = {
    _headStiffness = b
    runOnUiThread(new Runnable { def run = headStiffnessButton.setChecked(headStiffness) })
  }

  private var _goToPostureInProgress = false
  private def goToPostureInProgress = _goToPostureInProgress
  private def goToPostureInProgress_=(b: Boolean) {
    _goToPostureInProgress = b
    if (b) {
      setHeadStiffnessButtonEnabled(false)
    } else {
    }
  }

  private var deviceMode = Handheld

  private var hasInitializedPosition = false

  override def onCreate(savedInstanceState: Bundle) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_main)

    headStiffnessButton = findViewById(R.id.headStiffnessButton).asInstanceOf[ToggleButton]
    headStiffnessButton.setEnabled(false)
    headStiffnessButton.setOnClickListener(new OnClickListener {
      override def onClick(v: View) {
        setHeadStiffnessAndThenUpdateButton(!headStiffness)
      }
    })

    if (savedInstanceState != null) {
      if (savedInstanceState.containsKey(ACTORREF_STATE_KEY))
        actor = savedInstanceState.get(ACTORREF_STATE_KEY).asInstanceOf[ActorRef]
      if (savedInstanceState.containsKey(HAS_INITIALIZED_POSITION_STATE_KEY))
        hasInitializedPosition = savedInstanceState.getBoolean(HAS_INITIALIZED_POSITION_STATE_KEY)
      if (savedInstanceState.containsKey(HEAD_STIFFNESS_BUTTON_ENABLED_KEY))
        headStiffnessButton.setEnabled(savedInstanceState.getBoolean(HEAD_STIFFNESS_BUTTON_ENABLED_KEY))
    }

    Sensors.initSensors

    mainFrame = findViewById(R.id.mainActivityRelativeLayout).asInstanceOf[RelativeLayout]
    mainFrame.addView(CamView)

    mDetector = new GestureDetectorCompat(this, CamViewGestureListener);

    walkImageView = findViewById(R.id.walkImageView).asInstanceOf[ImageView]
    rotateImageView = findViewById(R.id.rotateImageView).asInstanceOf[ImageView]

    shapesLayout = findViewById(R.id.shapesLayout).asInstanceOf[RelativeLayout]

    val vto = shapesLayout.getViewTreeObserver
    vto.addOnGlobalLayoutListener(new OnGlobalLayoutListener() {
      override def onGlobalLayout {
        viewWidth.update(walkImageView, walkImageView.getMeasuredWidth)
        viewWidth.update(rotateImageView, rotateImageView.getMeasuredWidth)

        viewHeight.update(walkImageView, walkImageView.getMeasuredHeight)
        viewHeight.update(rotateImageView, rotateImageView.getMeasuredHeight)
      }
    })

    class AxisTouchListener(val xAxis: InputAxis, val yAxis: InputAxis) extends View.OnTouchListener {
      override def onTouch(v: View, event: MotionEvent) = {
        if (event.getAction == MotionEvent.ACTION_DOWN || event.getAction == MotionEvent.ACTION_MOVE) {
          val viewWidthWithoutMargin = viewWidth(v) - 2 * getResources.getDimension(R.dimen.shape_imageview_padding)
          val viewHeightWithoutMargin = viewHeight(v) - 2 * getResources.getDimension(R.dimen.shape_imageview_padding)
          if (xAxis != null) xAxis.value = (2 * event.getX - viewWidth(v)) / viewWidthWithoutMargin
          if (yAxis != null) yAxis.value = (2 * event.getY - viewHeight(v)) / viewHeightWithoutMargin
          true
        } else if (event.getAction == MotionEvent.ACTION_UP) {
          if (xAxis != null) xAxis.value = 0.0d
          if (yAxis != null) yAxis.value = 0.0d
          true
        } else false
      }
    }

    import InputAxes.Touchable.Push._
    walkImageView.setOnTouchListener(new AxisTouchListener(xWalkArea, yWalkArea))
    rotateImageView.setOnTouchListener(new AxisTouchListener(xRotateArea, null))

    RobotMotion

    Log.d(TAG, "onCreate done")
  }

  private object CamViewGestureListener extends GestureDetector.SimpleOnGestureListener {
    override def onScroll(event1: MotionEvent, event2: MotionEvent,
      distX: Float, distY: Float) = {
      Log.d(TAG, "onScroll: " + distX + "  " + distY);
      val oldXValue = InputAxes.Touchable.Scroll.xMove.value
      InputAxes.Touchable.Scroll.xMove.value = oldXValue - distX / SCROLL_SENSITIVITY
      val oldYValue = InputAxes.Touchable.Scroll.yMove.value
      InputAxes.Touchable.Scroll.yMove.value = oldYValue - distY / SCROLL_SENSITIVITY
      true
    }
  }

  override def onTouchEvent(event: MotionEvent) = {
    mDetector.onTouchEvent(event);
    super.onTouchEvent(event);
  }

  private def openConnectionDialog {
    val connectDialog = new Dialog(this)
    connectDialog.setContentView(R.layout.connect)
    connectDialog.setTitle("Connect to NaoActor")

    val connectButton = connectDialog.findViewById(R.id.connectButton).asInstanceOf[Button]
    val naoactorAddressEditText = connectDialog.findViewById(R.id.naoactorAddressEditText).asInstanceOf[EditText]

    prefs = getPreferences(Context.MODE_PRIVATE)
    if (prefs.contains(NAOACTOR_ADDRESS_PREF_KEY)) {
      naoactorAddressEditText.setText(prefs.getString(NAOACTOR_ADDRESS_PREF_KEY, ""))
    }

    connectButton.setOnClickListener(new View.OnClickListener() {
      override def onClick(v: View) {
        val addressText = naoactorAddressEditText
          .getText().toString()
        if (InetAddressUtils.isIPv4Address(addressText)) {
          if (toast != null) toast.cancel
          toast = Toast.makeText(getApplicationContext(), R.string.connecting, Toast.LENGTH_LONG)
          toast.show
          val editor = prefs.edit
          editor.putString(NAOACTOR_ADDRESS_PREF_KEY, addressText)
          editor.commit
          val remoteActorAddress = "akka://" + REMOTE_ACTOR_SYSTEM + "@" + addressText + ":" + REMOTE_ACTOR_PORT + "/user/" + REMOTE_ACTOR_NAME

          val future = actor ? Connect(remoteActorAddress)

          future.onFailure {
            case e: Exception => {
              runOnUiThread(new Runnable {
                def run {
                  toast.cancel
                  toast = Toast.makeText(getApplicationContext(), R.string.connection_error, Toast.LENGTH_LONG)
                  toast.show
                }
              })
            }
          }
          future.onSuccess {
            case s: Any => {
              runOnUiThread(new Runnable {
                def run {
                  connectDialog.dismiss
                  toast.cancel
                }
              })
              setupAfterConnectionHasEstablished
            }
          }
        } else {
          if (toast != null) toast.cancel
          toast = Toast.makeText(getApplicationContext(), R.string.invalid_address, Toast.LENGTH_LONG)
          toast.show
        }
      }
    });

    connectDialog.show
  }

  private def setupAfterConnectionHasEstablished {
    getTextToSpeechLanguagesAndUpdateMenu
    getHeadStiffnessAndUpdateButton

    if (OLD_NAOQI_VERSION) {
      val future = actor ? RobotRequest("BehaviorManager", "getInstalledBehaviors")
      extractAndExecute(future) {
        case x => println("Installed behaviors: " + x)
      }
    }
  }

  override def onCreateOptionsMenu(menu: Menu) = {
    getMenuInflater().inflate(R.menu.main, menu);

    val switchDeviceModeItem = menu.findItem(R.id.switchDeviceMode)
    val switchItemText = deviceMode match {
      case Handheld => (R.string.handheldModeText)
      case Table => (R.string.tableModeText)
    }
    switchDeviceModeItem.setTitle(switchItemText)

    val submenu = menu.findItem(R.id.languages).getSubMenu
    submenu.clear
    textToSpeechLanguages.foreach(str => {
      var id = Menu.FIRST
      submenu.add(languages_group, id, Menu.NONE, str)
      language2idMap.put(str, id)
      id += 1
    })
    submenu.setGroupCheckable(languages_group, true, true)
    Log.i(TAG, "onCreateOptionsMenu done")
    true
  }

  override def onOptionsItemSelected(item: MenuItem) = {
    if (item.getGroupId == languages_group && !item.hasSubMenu) {
      textToSpeechLanguage = item.getTitle.toString
      item.setChecked(true)
      Log.i(TAG, "Setting TextToSpeech language to '" + textToSpeechLanguage + "'")
      actor ! RobotRequest("TextToSpeech", "setLanguage", textToSpeechLanguage)
    } else if (item.getItemId == R.id.action_say) {
      openSayDialog
    } else if (item.getItemId == R.id.switchDeviceMode) {
      switchDeviceMode
    } else if (item.getItemId == R.id.disconnectAndExit) {
      actor ! Shutdown
      this.finish
    } else {}
    true
  }

  override def onResume() {
    super.onResume

    val akkaFileConfig = ConfigFactory.load.getConfig("remotelookup")
    val akkaMyConfig = ConfigFactory.parseString("akka.remote.netty.hostname=\"" + UtilsJava.getLocalIpAddress() + "\"")
    val akkaConfig = akkaMyConfig.withFallback(akkaFileConfig)

    val future = ask(actor, Status)(SUPERVISOR_ISALIVE_TIMEOUT)
    future.onFailure {
      case _ => {
        hasInitializedPosition = false
        actorSystem = ActorSystem(LOCAL_ACTOR_SYSTEM, akkaConfig)
        actor = actorSystem.actorOf(Props[SupervisorActor], "supervisorActor")
        runOnUiThread(new Runnable { def run = openConnectionDialog })
      }
    }
    future.foreach {
      case Connected => setupAfterConnectionHasEstablished
      case NotConnected => {
        hasInitializedPosition = false
        runOnUiThread(new Runnable { def run = openConnectionDialog })
      }
    }

    Sensors.registerListener
    CamView.start
    RobotMotion.start
    Log.d(TAG, "onResume done")
  }

  override def onPause() {
    Sensors.unregisterListener
    RobotMotion.stop
    CamView.stop
    actor ! RobotRequest("Motion", "stopMove")
    Log.d(TAG, "onPause done")
    super.onPause
  }

  //  override def onStop() {   
  //    Log.i(TAG, "onStop done")
  //    super.onStop
  //  }

  override def onDestroy() {
    mainFrame.removeView(CamView)
    //    camLayout.removeView(CamView)
    super.onDestroy
  }

  override def onSaveInstanceState(outState: Bundle) {
    outState.putSerializable(ACTORREF_STATE_KEY, actor)
    outState.putBoolean(HAS_INITIALIZED_POSITION_STATE_KEY, hasInitializedPosition)
    outState.putBoolean(HEAD_STIFFNESS_BUTTON_ENABLED_KEY, headStiffnessButton.isEnabled)
    super.onSaveInstanceState(outState)
  }

  def standUp(v: View) {
    standUp
  }

  def sitDown(v: View) {
    sitDown
  }

  private def standUp {
    if (OLD_NAOQI_VERSION) {
      Log.i(TAG, "Running behavior 'StandUp'")
      actor ! RobotRequest("BehaviorManager", "runBehavior", "StandUp")
      hasInitializedPosition = true
    } else {
      val future = ask(actor, RobotRequest("RobotPosture", "goToPosture", "Stand", 1.0d))(POSTURE_TIMEOUT)
      extractAndExecute(future) {
        case true => {
          Log.i(TAG, "Posture 'Stand' reached")
          getHeadStiffnessAndUpdateButton
          setHeadStiffnessButtonEnabled(false)
          goToPostureInProgress = false
          hasInitializedPosition = true
        }
        case false => {
          Log.w(TAG, "Moving to posture 'Stand' failed")
          goToPostureInProgress = false
        }
      }
    }
  }

  private def sitDown {
    if (OLD_NAOQI_VERSION) {
      Log.i(TAG, "Running behavior 'SitDown'")
      actor ! RobotRequest("BehaviorManager", "runBehavior", "SitDown")
      hasInitializedPosition = true
    } else {
      val future = ask(actor, RobotRequest("RobotPosture", "goToPosture", "Sit", 1.0d))(POSTURE_TIMEOUT)
      extractAndExecute(future) {
        case true => {
          Log.i(TAG, "Posture 'Sit' reached")
          setBodyStiffnessOff
          setHeadStiffnessButtonEnabled(true)
          goToPostureInProgress = false
          hasInitializedPosition = true
        }
        case false => {
          Log.w(TAG, "Moving to posture 'Sit' failed")
          goToPostureInProgress = false
        }
      }
    }

  }

  private def switchDeviceMode {
    deviceMode match {
      case Handheld => {
        deviceMode = Table
        Log.i(TAG, "Switching to table mode")
      }
      case Table => {
        deviceMode = Handheld
        Log.i(TAG, "Switching to handheld mode")
      }
    }
    invalidateOptionsMenu
  }

  private def getTextToSpeechLanguagesAndUpdateMenu {
    val future = actor ? RobotRequest("TextToSpeech", "getAvailableLanguages")
    extractAndExecute(future) {
      case x: List[_] => {
        Log.i(TAG, "Updating available TextToSpeech languages: " + x)
        textToSpeechLanguages = x.map(_.toString).toArray
        invalidateOptionsMenu
      }
    }
  }

  private def getHeadStiffnessAndUpdateButton {
    val future = actor ? RobotRequest("Motion", "getStiffnesses", "Head")
    extractAndExecute(future) {
      case x: List[_] => headStiffness = (x.forall(_ == 1.0d))
    }
  }

  private def setHeadStiffnessAndThenUpdateButton(enabled: Boolean) {
    val stiffnessValue = if (enabled) 1.0d else 0.0d
    val future = actor ? RobotRequest("Motion", "setStiffnesses", "Head", stiffnessValue)
    Log.i(TAG, "Set head stiffness to " + stiffnessValue)
    future.onSuccess {
      case _ => getHeadStiffnessAndUpdateButton
    }
  }

  private def setHeadStiffnessButtonEnabled(b: Boolean) {
    runOnUiThread(new Runnable { def run = headStiffnessButton.setEnabled(b) })
  }

  private def setBodyStiffnessOff {
    val stiffnessValue = 0.0d
    val future = actor ? RobotRequest("Motion", "setStiffnesses", "Body", stiffnessValue)
    Log.i(TAG, "Set body stiffness to " + stiffnessValue)
  }

  private def openSayDialog {
    val input = new EditText(this)
    new android.app.AlertDialog.Builder(this)
      .setTitle("Say something [language='" + textToSpeechLanguage + "']: ")
      .setView(input)
      .setPositiveButton("Say", new DialogInterface.OnClickListener {
        def onClick(dialog: DialogInterface, whichButton: Int) {
          val text = input.getText.toString
          Log.i(TAG, "TextToSpeech say " + text)
          actor ! RobotRequest("TextToSpeech", "say", text)
        }
      }).setNegativeButton("Cancel", new DialogInterface.OnClickListener {
        def onClick(dialog: DialogInterface, whichButton: Int) {
          // Do nothing.
        }
      }).show();
  }

  private object CamView extends View(this) {
    val TAG = "CamView"
    val REFRESH_RATE = 1000 / FRAMES_PER_SECOND

    val mPainter = new Paint();
    mPainter.setAntiAlias(true);

    val resources = getResources

    private var image = Array[Byte]()
    private var bitmap: Bitmap = null
    private var dummyBitmap: Bitmap = null

    private var future: ScheduledFuture[_] = null
    private var executor: ScheduledExecutorService = null

    def start {
      executor = Executors.newScheduledThreadPool(1)
      future = executor.scheduleWithFixedDelay(new Runnable() {
        override def run() {
          if (VIDEO_ENABLED) {
            getImageAndForceCanvasUpdate
          } else {
            getDummyImageAndForceCanvasUpdate
          }
        }
      }, 0, REFRESH_RATE, TimeUnit.MILLISECONDS);
      Log.i(TAG, "Cam View started")
    }

    override def onDraw(canvas: Canvas) {
      if (bitmap != null) {
        val ratio = 1.0f * bitmap.getWidth / bitmap.getHeight
        val target = if (canvas.getWidth / canvas.getHeight > ratio) {
          new RectF((canvas.getWidth - ratio * canvas.getHeight) / 2.0f, 0.0f, (canvas.getWidth + ratio * canvas.getHeight) / 2.0f, canvas.getHeight)
        } else {
          new RectF(0.0f, 0.0f, canvas.getWidth, canvas.getWidth / ratio)
        }
        canvas.drawBitmap(bitmap, null, target, mPainter)
      }
    }

    def stop {
      future.cancel(true)
    }

    private def setResolution {
      actor ! RobotRequest("VideoDevice", "setResolution", VIDEO_RESOLUTION)
      Log.i(TAG, "Set resolution to " + VIDEO_RESOLUTION)
    }

    private def getImageAndForceCanvasUpdate {
      val future = ask(actor, RobotRequest("VideoDevice", "getImage"))
      extractAndExecute(future)({
        case a: Array[Byte] => {
          this.image = a
          if (image.size > 0) {
            bitmap = BitmapFactory.decodeByteArray(image, 0, image.size)
            postInvalidate
          }
        }
      }, {
        case _ => {
          BitmapFactory.decodeResource(getResources(), R.drawable.testbild)
          postInvalidate
        }
      })
    }

    private def getDummyImageAndForceCanvasUpdate {
      if (dummyBitmap == null) {
        dummyBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.nao_stand)
      }
      bitmap = dummyBitmap
      postInvalidate
    }

  }

  private object RobotMotion {
    val MIN_PITCH = -38.5d / 180 * PI
    val MAX_PITCH = 29.5d / 180 * PI

    val MIN_YAW = -119.5d / 180 * PI
    val MAX_YAW = 119.5d / 180 * PI

    private var future: ScheduledFuture[_] = null

    private val executor = Executors.newScheduledThreadPool(1)
    def start {
      future = executor.scheduleWithFixedDelay(new Runnable() {
        override def run() {
          if (hasInitializedPosition && !goToPostureInProgress) {
            import NaoAxes._
            val config = InputConfiguration.currentConfig(deviceMode)
            // head
            val yaw = config(headYaw).value
            val pitch = config(headPitch).value
            setHeadAngles(yaw, pitch, HEAD_SPEED)

            // walk + rotate
            val walkX = config(moveX).value
            val walkY = config(moveY).value
            val rotation = config(rotate).value
            setWalkVelocity(walkX, walkY, rotation, WALK_SPEED)
          }
        }
      }, 0, ROBOT_MOTION_REFRESH_RATE, TimeUnit.MILLISECONDS);
    }
    def stop {
      future.cancel(true)
    }
    private def setHeadAngles(yaw: Double, pitch: Double, speed: Double) {
      actor ! RobotRequest("Motion", "setAngles", List("HeadYaw", "HeadPitch"), List(yaw, pitch), speed)
      Log.d(TAG, "Motion setAngles HeadYaw/HeadPitch " + yaw + "/" + pitch)
    }

    private def setWalkVelocity(x: Double, y: Double, rotate: Double, frequency: Double) {
      actor ! RobotRequest("Motion", "setWalkTargetVelocity", y, x, rotate, frequency)
      Log.d(TAG, "Motion setWalkTargetVelocity y / x / rotate " + y + "/" + x + "/" + rotate)
    }

  }

  private object Sensors extends SensorEventListener {
    import android.view.Surface._
    import android.hardware.SensorManager._
    import InputAxes.Sensors._

    val TAG = "Sensors"
    var sensorManager: SensorManager = null
    var accelerometer: Sensor = null
    var magnetometer: Sensor = null

    var mDisplay: android.view.Display = null

    var gravity: Array[Float] = null
    var geomagnetic: Array[Float] = null

    var oldAzimuthValue = 0.0

    val deviceAxisMapping: Map[Int, (Int, Int)] = Map(
      ROTATION_0 -> (AXIS_X, AXIS_Z),
      ROTATION_90 -> (AXIS_Z, AXIS_MINUS_X),
      ROTATION_180 -> (AXIS_MINUS_X, AXIS_MINUS_Z),
      ROTATION_270 -> (AXIS_MINUS_Z, AXIS_X))

    def initSensors {
      import Context._

      sensorManager = getSystemService(SENSOR_SERVICE).asInstanceOf[SensorManager]
      accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
      magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

      val mWindowManager = getSystemService(WINDOW_SERVICE).asInstanceOf[android.view.WindowManager]
      mDisplay = mWindowManager.getDefaultDisplay()
      Log.d(TAG, "Sensor initialization done")
    }

    def registerListener {
      sensorManager.registerListener(this, accelerometer, SENSOR_DELAY)
      sensorManager.registerListener(this, magnetometer, SENSOR_DELAY);
      Log.d(TAG, "Sensor listener registered")
    }

    def unregisterListener {
      sensorManager.unregisterListener(this)
      Log.d(TAG, "Sensor listener unregistered")
    }

    override def onSensorChanged(event: SensorEvent) {
      if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
        gravity = event.values.clone
        Log.v(TAG, "Got accelerometer data")
      } else if (event.sensor.getType == Sensor.TYPE_MAGNETIC_FIELD) {
        geomagnetic = event.values.clone
        Log.v(TAG, "Got magnetic field data")
      }
      if (gravity != null && geomagnetic != null) {
        val rotationMatrix = new Array[Float](9)
        val remappedRotationMatrix = new Array[Float](9)
        SensorManager.getRotationMatrix(rotationMatrix,
          null, gravity, geomagnetic)

        val (xAxis, yAxis) = deviceAxisMapping(mDisplay.getOrientation)
        val success = SensorManager.remapCoordinateSystem(rotationMatrix, xAxis, yAxis, remappedRotationMatrix)
        if (success) {
          val orientationMatrix = new Array[Float](3)
          SensorManager.getOrientation(remappedRotationMatrix, orientationMatrix)
          val azimuthDiff = orientationMatrix(0) - oldAzimuthValue
          azimuth.value = azimuth.value + azimuthDiff
          oldAzimuthValue = orientationMatrix(0)
          pitch.value = orientationMatrix(1)
          roll.value = orientationMatrix(2)
          Log.v(TAG, "azimuth=" + azimuth.value + " pitch=" + pitch.value + " roll=" + roll.value)
        }
      }
    }

    override def onAccuracyChanged(sensor: Sensor, accuracy: Int) {
      // N/A
    }
  }
}
