package robots.naoactorclient

import android.app.Activity
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

import com.example.naoactorclient.R;

import android.graphics.drawable.BitmapDrawable
import android.graphics.BitmapFactory
import android.widget.ImageView
import android.graphics.drawable.ShapeDrawable
import android.view.ViewTreeObserver
import android.view.ViewTreeObserver.OnGlobalLayoutListener

class MainActivity extends Activity {

  val TAG = "MainActivity"
  val languages_group = R.id.languages_group
  val context = this.asInstanceOf[Context]
  val activity = this
  var mainFrame: RelativeLayout = null
  var shapesLayout: RelativeLayout = null
  var walkImageView: ImageView = null
  var headImageView: ImageView = null
  var rotateImageView: ImageView = null

  val viewWidth = collection.mutable.Map[View, Float]()
  val viewHeight = collection.mutable.Map[View, Float]()

  var textToSpeechLanguages = Array[String]()
  var textToSpeechLanguage = ""
  var language2idMap = collection.mutable.Map[String, Int]()

  override def onCreate(savedInstanceState: Bundle) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_main)
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

    val actor = Actors.system.actorOf(Props[GetTextToSpeechLanguagesActor], "getLanguagesActor")
    actor ! this
    actor ! RobotRequest("TextToSpeech", "getAvailableLanguages")

    val STEP_FREQUENCY = 1.0d

    class AxisTouchListener(val xAxis: InputAxis, val yAxis: InputAxis) extends View.OnTouchListener {
      override def onTouch(v: View, event: MotionEvent) = {
        if (event.getAction == MotionEvent.ACTION_DOWN || event.getAction == MotionEvent.ACTION_MOVE) {
          val viewWidthWithoutMargin = viewWidth(v) - 2 * getResources.getDimension(R.dimen.shape_imageview_padding)
          val viewHeightWithoutMargin = viewWidth(v) - 2 * getResources.getDimension(R.dimen.shape_imageview_padding)
          if (xAxis != null) xAxis.value = (2 * event.getX - viewWidth(v)) / viewWidthWithoutMargin
          if (yAxis != null) yAxis.value = (2 * event.getY - viewHeight(v)) / viewHeightWithoutMargin
          true
        } else false
      }
    }

    walkImageView.setOnTouchListener(new AxisTouchListener(TouchableAxis.x1, TouchableAxis.y1))
    headImageView.setOnTouchListener(new AxisTouchListener(TouchableAxis.x2, TouchableAxis.y2))
    rotateImageView.setOnTouchListener(new AxisTouchListener(TouchableAxis.x3, null))
  }

  override def onCreateOptionsMenu(menu: Menu) = {
    getMenuInflater().inflate(R.menu.main, menu);

    val submenu = menu.findItem(R.id.languages).getSubMenu
    submenu.clear
import Menu.NONE
    textToSpeechLanguages.foreach(str => {
      var id = Menu.FIRST
      submenu.add(languages_group, id, NONE, str)
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
      Actors.remoteActor ! RobotRequest("TextToSpeech", "setLanguage", item.getTitle.toString)
    } else if (item.getItemId == R.id.action_say) {
      openSayDialog
    }
    true
  }

  override def onPause() {
    Sensors.unregisterListener
    super.onPause
  }

  override def onStop() {
    CamView.stop
    Actors.system.shutdown();
    Log.i(TAG, "Returned from ActorSystem.shutdown")
    super.onStop
  }

  override def onResume() {
    super.onResume
    Sensors.registerListener
  }

  def setTextToSpeechLanguages(lang: Array[String]) = {
    textToSpeechLanguages = lang
    invalidateOptionsMenu
  }

  private def openSayDialog {
    val input = new EditText(this)
    new android.app.AlertDialog.Builder(this)
      .setTitle("Say something: ")
      .setView(input)
      .setPositiveButton("Say", new DialogInterface.OnClickListener {
        def onClick(dialog: DialogInterface, whichButton: Int) {
          val value = input.getText
          Actors.remoteActor ! RobotRequest("TextToSpeech", "say", value.toString)
        }
      }).setNegativeButton("Cancel", new DialogInterface.OnClickListener {
        def onClick(dialog: DialogInterface, whichButton: Int) {
          // Do nothing.
        }
      }).show();
  }

  def setImage(image: Array[Byte]) {
    CamView.setImage(image)
  }

  private object CamView extends View(context) {
    val FPS = 5
    val REFRESH_RATE = 1000 / FPS

    val mPainter = new Paint();
    mPainter.setAntiAlias(true);

    val resources = getResources

    private var image = Array[Byte]()

    private val executor = Executors.newScheduledThreadPool(1)

    val getImageActor = Actors.system.actorOf(Props[GetImageActor], "getImageActor")
    getImageActor ! MainActivity.this
    getImageActor ! RobotRequest("VideoDevice", "setResolution", robots.common.Resolution.QVGA)

    private val future = executor.scheduleWithFixedDelay(new Runnable() {
      override def run() {
        getImageActor ! RobotRequest("VideoDevice", "getImage")
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

    def setImage(image: Array[Byte]) {
      if (image != null) this.image = image
      postInvalidate
    }

    def stop {
      future.cancel(true)
      mainFrame.post(new Runnable() {
        override def run() {
          mainFrame.removeView(CamView);
          Log.i(TAG, "Cam View removed")
        }
      })
    }
  }

  private object InputControl {
    val REFRESH_RATE = 200

    val HEAD_SPEED = 1.0d

    val MIN_PITCH = -38.5d
    val MAX_PITCH = 29.5d

    val MIN_YAW = -119.5d
    val MAX_YAW = 119.5d

    private val executor = Executors.newScheduledThreadPool(1)
    private val future = executor.scheduleWithFixedDelay(new Runnable() {
      override def run() {
        // head
        val yaw = Utils.between(MIN_YAW, TouchableAxis.x2.value * MAX_YAW, MAX_YAW)
        val pitch = TouchableAxis.y2.value
        Log.d("InputControl", "Motion setAngles HeadYaw/HeadPitch " + yaw + "/" + pitch)
        Actors.remoteActor ! RobotRequest("Motion", "setAngles", List("HeadYaw", "HeadPitch"), List(yaw, pitch), HEAD_SPEED)
      }
    }, 0, REFRESH_RATE, TimeUnit.MILLISECONDS);

  }

  private object TouchableAxis {
    val x1 = new InputAxis("x1")
    val y1 = new InputAxis("y1")
    val x2 = new InputAxis("x2")
    val y2 = new InputAxis("y2")
    val x3 = new InputAxis("x3")
  }

  private object Sensors extends SensorEventListener {
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
      magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);

    }

    def registerListener {
      val DELAY = SensorManager.SENSOR_DELAY_NORMAL
      sensorManager.registerListener(this, accelerometer, DELAY)
      sensorManager.registerListener(this, magnetometer, DELAY);
    }

    def unregisterListener {
      sensorManager.unregisterListener(this)
    }

    override def onSensorChanged(event: SensorEvent) {
      var gravity: Array[Float] = null
      var geomagnetic: Array[Float] = null
      if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
        gravity = event.values.clone
      } else if (event.sensor.getType == Sensor.TYPE_MAGNETIC_FIELD) {
        geomagnetic = event.values.clone
      }
      if (gravity != null && geomagnetic != null) {
        val rotationMatrix = Array[Float]()
        val success = SensorManager.getRotationMatrix(rotationMatrix,
          null, gravity, geomagnetic)
        if (success) {
          val orientationMatrix = Array[Float]()
          SensorManager.getOrientation(rotationMatrix, orientationMatrix)
          xGravity.value = Math.asin(gravity(0))
          azimuth.value = orientationMatrix(0)
          pitch.value = orientationMatrix(1)
          roll.value = orientationMatrix(2)
        }
      }
    }

    override def onAccuracyChanged(sensor: Sensor, accuracy: Int) {
      // N/A
    }
  }
}