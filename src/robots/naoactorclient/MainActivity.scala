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
import robots.naoactorclient.localactors.SupervisorActor
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

class MainActivity extends Activity {

  val TAG = "MainActivity"
  val NAOACTOR_ADDRESS_PREF = "naoactor_adress"
  val languages_group = R.id.languages_group
  //  val context = this.asInstanceOf[Context]
  //  val activity = this
  private var prefs: SharedPreferences = null
  private var toast: Toast = null

  private var mainFrame: RelativeLayout = null
  private var shapesLayout: RelativeLayout = null
  private var walkImageView: ImageView = null
  private var headImageView: ImageView = null
  private var rotateImageView: ImageView = null

  private val viewWidth = collection.mutable.Map[View, Float]()
  private val viewHeight = collection.mutable.Map[View, Float]()

  private var textToSpeechLanguages = Array[String]()
  private var textToSpeechLanguage = ""
  private var language2idMap = collection.mutable.Map[String, Int]()

  implicit val ROBOTREQUEST_TIMEOUT = Timeout(5 seconds)
  val POSTURE_TIMEOUT = Timeout(10 seconds)
  private var actorSystem: ActorSystem = null
  private var actor: ActorRef = null

  private var headStiffnessButton: ToggleButton = null

  //  private var headStiffnesses = Array(0.0d)
  private var _headStiffness = false
  private def headStiffness = _headStiffness
  private def headStiffness_=(b: Boolean) = {
    _headStiffness = b
    headStiffnessButton.setChecked(headStiffness)
  }

  private var _goToPostureInProgress = false
  private def goToPostureInProgress = _goToPostureInProgress
  private def goToPostureInProgress_=(b: Boolean) {
    _goToPostureInProgress = b
    if (b) {
      setHeadStiffnessButtonEnabled(false)
      //disable all buttons
    } else {
      //enable all buttons ?
    }
  }

  private var hasInitializedPosition = false

  val VIDEO_ENABLED = false
  val VIDEO_RESOLUTION = robots.common.Resolution.QVGA
//  val STIFFNESS_OFF_AFTER_SITDOWN = true

  val OLD_NAOQI_VERSION = true

  override def onCreate(savedInstanceState: Bundle) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_main)

    //    val messenger = new Messenger(new ReplyHandler(this))
    //    actor ! messenger
    //    actor ! Actors.remoteActor

    Sensors.initSensors

    mainFrame = findViewById(R.id.mainActivityRelativeLayout).asInstanceOf[RelativeLayout]
    mainFrame.addView(CamView)

    walkImageView = findViewById(R.id.walkImageView).asInstanceOf[ImageView]
    headImageView = findViewById(R.id.headImageView).asInstanceOf[ImageView]
    rotateImageView = findViewById(R.id.rotateImageView).asInstanceOf[ImageView]

    shapesLayout = findViewById(R.id.shapesLayout).asInstanceOf[RelativeLayout]

    val vto = shapesLayout.getViewTreeObserver
    vto.addOnGlobalLayoutListener(new OnGlobalLayoutListener() {
      override def onGlobalLayout {
        viewWidth.update(walkImageView, walkImageView.getMeasuredWidth)
        viewWidth.update(headImageView, headImageView.getMeasuredWidth)
        viewWidth.update(rotateImageView, rotateImageView.getMeasuredWidth)

        viewHeight.update(walkImageView, walkImageView.getMeasuredHeight)
        viewHeight.update(headImageView, headImageView.getMeasuredHeight)
        viewHeight.update(rotateImageView, rotateImageView.getMeasuredHeight)
      }
    })

    headStiffnessButton = findViewById(R.id.headStiffnessButton).asInstanceOf[ToggleButton]
    headStiffnessButton.setEnabled(false)
    headStiffnessButton.setOnClickListener(new OnClickListener {
      override def onClick(v: View) {
        setHeadStiffnessAndThenUpdateButton(headStiffness)
      }
    })

    val STEP_FREQUENCY = 1.0d

    class AxisTouchListener(val xAxis: InputAxis, val yAxis: InputAxis) extends View.OnTouchListener {
      override def onTouch(v: View, event: MotionEvent) = {
        if (event.getAction == MotionEvent.ACTION_DOWN || event.getAction == MotionEvent.ACTION_MOVE) {
          val viewWidthWithoutMargin = viewWidth(v) - 2 * getResources.getDimension(R.dimen.shape_imageview_padding)
          val viewHeightWithoutMargin = viewHeight(v) - 2 * getResources.getDimension(R.dimen.shape_imageview_padding)
          if (xAxis != null) xAxis.value = (2 * event.getX - viewWidth(v)) / viewWidthWithoutMargin
          if (yAxis != null) yAxis.value = (2 * event.getY - viewHeight(v)) / viewHeightWithoutMargin
          true
        } else if (event.getAction == MotionEvent.ACTION_UP) {
          if (xAxis != null) xAxis.value = Double.NaN
          if (yAxis != null) yAxis.value = Double.NaN
          true
        } else false
      }
    }

    walkImageView.setOnTouchListener(new AxisTouchListener(TouchableAxis.x1, TouchableAxis.y1))
    headImageView.setOnTouchListener(new AxisTouchListener(TouchableAxis.x2, TouchableAxis.y2))
    rotateImageView.setOnTouchListener(new AxisTouchListener(TouchableAxis.x3, null))

    InputControl

    Log.d(TAG, "onCreate done")
  }

  private def openConnectionDialog {
    val connectDialog = new Dialog(this)
    connectDialog.setContentView(R.layout.connect)
    connectDialog.setTitle("Connect to NaoActor")

    val connectButton = connectDialog.findViewById(R.id.connectButton).asInstanceOf[Button]
    val naoactorAddressEditText = connectDialog.findViewById(R.id.naoactorAddressEditText).asInstanceOf[EditText]

    val prefs = getPreferences(Context.MODE_PRIVATE)
    if (prefs.contains(NAOACTOR_ADDRESS_PREF)) {
      naoactorAddressEditText.setText(prefs.getString(NAOACTOR_ADDRESS_PREF, ""))
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
          editor.putString(NAOACTOR_ADDRESS_PREF, addressText)
          editor.commit
          val remoteActorAddress = "akka://" + getResources.getString(R.string.remote_actor_system) + "@" + addressText + ":" + getResources.getString(R.string.remote_actor_port) + "/user/" + getResources.getString(R.string.remote_actor)

          val future = actor ? localactors.Connect(remoteActorAddress)

          future.onFailure {
            case e: Exception => {
              println("EXCP  " + e)
              toast.cancel
              toast = Toast.makeText(getApplicationContext(), R.string.connection_error, Toast.LENGTH_LONG)
              toast.show
            }
          }
          future.onSuccess {
            case s: String => {
              println(s)
              toast.cancel
              connectDialog.dismiss
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

    if (OLD_NAOQI_VERSION) {
      val future = actor ? RobotRequest("BehaviorManager", "getInstalledBehaviors")
      extractAndExecute(future) {
        case x => println("Installed behaviors: " + x)
      }
    }
  }

  override def onCreateOptionsMenu(menu: Menu) = {
    getMenuInflater().inflate(R.menu.main, menu);

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
    }
    true
  }

  override def onPause() {
    Sensors.unregisterListener
    InputControl.stop
    Log.d(TAG, "onPause done")
    super.onPause
  }

  override def onStop() {
    CamView.stop
    actor ! localactors.Shutdown
    Log.i(TAG, "onStop done")
    super.onStop
  }

  override def onResume() {
    super.onResume

    val akkaFileConfig = ConfigFactory.load.getConfig("remotelookup")
    val akkaMyConfig = ConfigFactory.parseString("akka.remote.netty.hostname=\"" + Utils.getLocalIpAddress() + "\"")
    val akkaConfig = akkaMyConfig.withFallback(akkaFileConfig)

    val res = getResources
    actorSystem = ActorSystem(res.getString(R.string.actor_system), akkaConfig)
    actor = actorSystem.actorOf(Props[SupervisorActor], "supervisorActor")

    openConnectionDialog
    Sensors.registerListener
    Log.d(TAG, "onResume done")
  }

  private def extractAndExecute(fut: Future[Any])(func: Function1[Any, Unit]) = {
    fut.foreach(s1 => s1 match {
      case s2: Success[_] => s2.foreach(o => o match {
        case some: Some[_] => func(some.get)
        case _ =>
      })
      case _ =>
    })
  }

  def standUp(v: View) {
    standUp
  }

  def sitDown(v: View) {
    sitDown(true)
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

  private def sitDown(enableHeadStiffnessAfterSitDown: Boolean) {
    if (OLD_NAOQI_VERSION) {
      Log.i(TAG, "Running behavior 'SitDown'")
      actor ! RobotRequest("BehaviorManager", "runBehavior", "SitDown")
      hasInitializedPosition = true
    } else {
      val future = ask(actor, RobotRequest("RobotPosture", "goToPosture", "Sit", 1.0d))(POSTURE_TIMEOUT)
      extractAndExecute(future) {
        case true => {
          Log.i(TAG, "Posture 'Stand' reached")
          if (enableHeadStiffnessAfterSitDown) setHeadStiffnessAndThenUpdateButton(true) else getHeadStiffnessAndUpdateButton
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
    val stiffnessValue = if (headStiffness) 1.0d else 0.0d
    val future = actor ? RobotRequest("Motion", "setStiffnesses", "Head", stiffnessValue)
    Log.i(TAG, "Set head stiffness to " + stiffnessValue)
    future.onSuccess {
      case _ => getHeadStiffnessAndUpdateButton
    }
  }

  private def setHeadStiffnessButtonEnabled(b: Boolean) {
    mainFrame.post(new Runnable {
      def run {
        headStiffnessButton.setEnabled(b)
      }
    })
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

  //  def setImage(image: Array[Byte]) {
  //    CamView.setImage(image)
  //  }

  private object CamView extends View(this) {
    val TAG = "CamView"
    val FPS = 5
    val REFRESH_RATE = 1000 / FPS

    val mPainter = new Paint();
    mPainter.setAntiAlias(true);

    val resources = getResources

    private var image = Array[Byte]()

    private val executor = Executors.newScheduledThreadPool(1)

    private val future = executor.scheduleWithFixedDelay(new Runnable() {
      override def run() {
        if (VIDEO_ENABLED) {
          getImageAndForceCanvasUpdate
        }
      }
    }, 0, REFRESH_RATE, TimeUnit.MILLISECONDS);

    override def onDraw(canvas: Canvas) {
      val image = this.image.clone
      if (image.size > 0) {
        val bitmap = BitmapFactory.decodeByteArray(image, 0, image.size)
        if (bitmap != null) {
          val ratio = 1.0f * bitmap.getWidth / bitmap.getHeight
          val target = new RectF(0.0f, 0.0f, canvas.getWidth, canvas.getWidth / ratio)
          canvas.drawBitmap(bitmap, null, target, mPainter)
        }
      }
    }

    //    def setImage(image: Array[Byte]) {
    //      if (image != null) this.image = image
    //      postInvalidate
    //    }

    def stop {
      future.cancel(true)
      mainFrame.post(new Runnable() {
        override def run() {
          mainFrame.removeView(CamView);
          Log.i(TAG, "Cam View removed")
        }
      })
    }

    private def setResolution {
      actor ! RobotRequest("VideoDevice", "setResolution", VIDEO_RESOLUTION)
      Log.i(TAG, "Set resolution to " + VIDEO_RESOLUTION)
    }

    private def getImageAndForceCanvasUpdate {
      val future = ask(actor, RobotRequest("VideoDevice", "getImage")) //(new Timeout(REFRESH_RATE, TimeUnit.MILLISECONDS))
      extractAndExecute(future) {
        case l: List[_] => {
          this.image = l.asInstanceOf[Array[Byte]]
          postInvalidate
        }
      }
    }
  }

  private object InputControl {
    import Math.PI

    val REFRESH_RATE = 500

    val HEAD_SPEED = 1.0d
    val WALK_SPEED = 1.0d

    val MIN_PITCH = -38.5d / 180 * PI
    val MAX_PITCH = 29.5d / 180 * PI

    val MIN_YAW = -119.5d / 180 * PI
    val MAX_YAW = 119.5d / 180 * PI

    private val executor = Executors.newScheduledThreadPool(1)
    private val future = executor.scheduleWithFixedDelay(new Runnable() {
      override def run() {
        if (hasInitializedPosition && !goToPostureInProgress) {
          // head
          val yaw = if (TouchableAxis.x2.value.isNaN) Sensors.pitch.value else -Utils.between(MIN_YAW, TouchableAxis.x2.value * MAX_YAW, MAX_YAW)
          val pitch = if (TouchableAxis.y2.value.isNaN) Sensors.roll.value else TouchableAxis.y2.value
          setHeadAngles(yaw, pitch, HEAD_SPEED)

          // walk + rotate
          val walkX = -TouchableAxis.x1.value
          val walkY = -TouchableAxis.y1.value
          val rotate = -TouchableAxis.x3.value
          setWalkVelocity(walkX, walkY, rotate, WALK_SPEED)
        }
      }
    }, 0, REFRESH_RATE, TimeUnit.MILLISECONDS);

    def stop {
      future.cancel(true)
    }
  }

  private def setHeadAngles(yaw: Double, pitch: Double, speed: Double) {
    actor ! RobotRequest("Motion", "setAngles", List("HeadYaw", "HeadPitch"), List(yaw, pitch), speed)
    Log.d(TAG, "Motion setAngles HeadYaw/HeadPitch " + yaw + "/" + pitch)
  }

  private def setWalkVelocity(x: Double, y: Double, rotate: Double, frequency: Double) {
    actor ! RobotRequest("Motion", "setWalkTargetVelocity", y, x, rotate, frequency)
    Log.d(TAG, "Motion setWalkTargetVelocity y / x / rotate " + y + "/" + x + "/" + rotate)
  }

  private object TouchableAxis {
    val x1 = new InputAxis("x1")
    val y1 = new InputAxis("y1")
    val x2 = new InputAxis("x2")
    val y2 = new InputAxis("y2")
    val x3 = new InputAxis("x3")
  }

  private object Sensors extends SensorEventListener {
    val TAG = "Sensors"
    var sensorManager: SensorManager = null
    var accelerometer: Sensor = null
    var magnetometer: Sensor = null

    var gravity: Array[Float] = null
    var geomagnetic: Array[Float] = null

    val xGravity = new InputAxis("x-Gravity")

    val azimuth = new InputAxis("Azimuth")
    val pitch = new InputAxis("Pitch")
    val roll = new InputAxis("Roll")

    def initSensors {
      sensorManager = getSystemService(Context.SENSOR_SERVICE).asInstanceOf[SensorManager]
      accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
      magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
      Log.d(TAG, "Sensor initialization done")
    }

    def registerListener {
      val DELAY = SensorManager.SENSOR_DELAY_NORMAL
      sensorManager.registerListener(this, accelerometer, DELAY)
      sensorManager.registerListener(this, magnetometer, DELAY);
      Log.d(TAG, "Sensor listener registered")
    }

    def unregisterListener {
      sensorManager.unregisterListener(this)
      Log.d(TAG, "Sensor listener unregistered")
    }

    override def onSensorChanged(event: SensorEvent) {
      //      var gravity: Array[Float] = null
      //      var geomagnetic: Array[Float] = null
      if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
        Log.v(TAG, "Got accelerometer data")
        gravity = event.values.clone
      } else if (event.sensor.getType == Sensor.TYPE_MAGNETIC_FIELD) {
        geomagnetic = event.values.clone
        Log.v(TAG, "Got magnetic field data")
      }
      if (gravity != null && geomagnetic != null) {
        val rotationMatrix = new Array[Float](9)
        val success = SensorManager.getRotationMatrix(rotationMatrix,
          null, gravity, geomagnetic)
        if (success) {
          val orientationMatrix = new Array[Float](3)
          SensorManager.getOrientation(rotationMatrix, orientationMatrix)
          xGravity.value = Math.asin(gravity(0))
          azimuth.value = orientationMatrix(0)
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

  class ReplyHandler(ma: MainActivity) extends Handler {
    import robots.naoactorclient.localactors.{ ReplyMessageKeys => Keys }
    val mainActivity = new WeakReference[MainActivity](ma)
    override def handleMessage(msg: Message) {
      val activityOpt = mainActivity.get
      activityOpt match {
        case Some(activity) => {
          val data = msg.getData
          // ...
        }
        case _ =>
      }
    }
  }

}