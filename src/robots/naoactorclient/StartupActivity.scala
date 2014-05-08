package robots.naoactorclient

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.content.Context
import android.content.SharedPreferences
import android.view.View
import org.apache.http.conn.util.InetAddressUtils
import android.widget.Toast
import android.os.AsyncTask

import com.example.naoactorclient.R;
import com.typesafe.config.ConfigFactory
import akka.actor.ActorSystem
import akka.actor.Props
import android.content.Intent
import android.view.Menu
import scala.util.Try
import scala.util.Success
import scala.util.Failure
import android.app.AlertDialog
import android.app.DialogFragment
import android.content.DialogInterface
import android.util.Log

class StartupActivity extends Activity {

  val TAG = "StartupActivity"
  val NAOACTOR_ADDRESS = "naoactor_adress"
  val activity = this
  var prefs: SharedPreferences = null
  var toast: Toast = null
  val context = this.asInstanceOf[Context]
  override def onCreate(savedInstanceState: Bundle) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_startup)

    val connectButton = findViewById(R.id.connectButton).asInstanceOf[Button]
    val naoactorAddressEditText = findViewById(R.id.naoactorAddressEditText).asInstanceOf[EditText]

    prefs = getPreferences(Context.MODE_PRIVATE)
    if (prefs.contains(NAOACTOR_ADDRESS)) {
      naoactorAddressEditText.setText(prefs.getString(NAOACTOR_ADDRESS, ""))
    }

    connectButton.setOnClickListener(new View.OnClickListener() {
      override def onClick(v: View) {
        val addressText = naoactorAddressEditText
          .getText().toString()
        if (InetAddressUtils.isIPv4Address(addressText)) {
          toast = Toast.makeText(getApplicationContext(), R.string.connecting, Toast.LENGTH_LONG)
          toast.show
          val editor = prefs.edit
          editor.putString(NAOACTOR_ADDRESS, addressText)
          editor.commit
          new ConnectingTask().execute(addressText, Utils.getLocalIpAddress())
        } else {
          Toast.makeText(getApplicationContext(), R.string.invalid_address, Toast.LENGTH_LONG).show()
        }
      }
    });
  }

  override def onCreateOptionsMenu(menu: Menu): Boolean = {
    getMenuInflater().inflate(R.menu.main, menu)
    return true
  }

  class ConnectingTask extends AsyncTask[AnyRef, Unit, Try[Unit]] {
    override def doInBackground(args: AnyRef*) = {
      val remoteIp = args(0).toString
      val localIp = args(1).toString
      val remoteActorAddress = "akka://NaoActor@" + remoteIp + ":2552/user/nao"
      Log.i(TAG, "Connecting to " + remoteActorAddress)
      val fileConfig = ConfigFactory.load.getConfig("remotelookup")
      val myConfig = ConfigFactory.parseString("akka.remote.netty.hostname=\"" + localIp + "\"")
      val config = myConfig.withFallback(fileConfig)
      Try({
        val system =
          ActorSystem(getResources.getString(R.string.actor_system), config)
        val remoteActor = system.actorFor(remoteActorAddress)
        Actors.system = system
        Actors.remoteActor = remoteActor
      })
    }

    override def onPostExecute(result: Try[Unit]) = {
      result match {
        case _: Success[Unit] => {
          toast.setText(R.string.connected)
          toast.show
          val intent = new Intent(context, classOf[MainActivity])
          startActivity(intent)
        }
        case Failure(e) => {
          toast = Toast.makeText(getApplicationContext(), e.toString(), Toast.LENGTH_LONG)
          toast.show
        }
      }
    }
  }

}