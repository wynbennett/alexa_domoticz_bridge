package domoticz;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.eclipse.jetty.util.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazon.speech.slu.Intent;
import com.amazon.speech.slu.Slot;
import com.amazon.speech.speechlet.IntentRequest;
import com.amazon.speech.speechlet.LaunchRequest;
import com.amazon.speech.speechlet.Session;
import com.amazon.speech.speechlet.SessionEndedRequest;
import com.amazon.speech.speechlet.SessionStartedRequest;
import com.amazon.speech.speechlet.Speechlet;
import com.amazon.speech.speechlet.SpeechletException;
import com.amazon.speech.speechlet.SpeechletResponse;
import com.amazon.speech.ui.PlainTextOutputSpeech;
import com.amazon.speech.ui.Reprompt;
import com.amazon.speech.ui.SimpleCard;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.ReadContext;

import nl.hnogames.domoticzapi.Domoticz;
import nl.hnogames.domoticzapi.DomoticzValues;
import nl.hnogames.domoticzapi.Containers.DevicesInfo;
import nl.hnogames.domoticzapi.Containers.ServerInfo;
import nl.hnogames.domoticzapi.Containers.SwitchInfo;
import nl.hnogames.domoticzapi.Interfaces.DevicesReceiver;
import nl.hnogames.domoticzapi.Interfaces.setCommandReceiver;

public class DomoticzSpeechlet implements Speechlet {

  private static final Logger log = LoggerFactory.getLogger(DomoticzSpeechlet.class);

  /**
   * The key to get the item from the intent.
   */
  private static final String SWITCH_SLOT = "Switch";
  private static final String STATE_SLOT = "State";
  private static final String TEMPERATURE_SLOT = "Temperature";
  private static final String THERMOSTAT_SETPOINT = "ThermostatSetpoint";
  private static final String THERMOSTAT_MODE = "ThermostatMode";
  private static final String THERMOSTAT_MODE_VALUE = "ThermostatModeValue";
  private static final String THERMOSTAT = "Thermostat";
  private static final String CHANGE_SLOT = "Change";
  private static final String TEMPSENSOR_SLOT = "TempSensor";

  private static final String USERNAME = System.getenv("DOMOTICZ_USERNAME");
  private static final String PASSWORD = System.getenv("DOMOTICZ_PASSWORD");
  private static final String HOSTNAME = System.getenv("DOMOTICZ_HOSTNAME");
  private static final String PORT = System.getenv("DOMOTICZ_PORT");

  private static final String SERVER = "http://" + HOSTNAME + ":" + PORT;

  private static final String DOMOTICZ_LIGHT_LIST_URL =
      SERVER + "/json.htm?type=devices&filter=light&used=true&order=Name";
  private static final String DOMOTICZ_TEMP_LIST_URL =
      SERVER + "/json.htm?type=devices&used=true&order=Name";
  private static final String DOMOTICZ_WEATHER_LIST_URL =
      SERVER + "/json.htm?type=devices&filter=weather&used=true&order=Name";
  private static final String DOMOTICZ_UTILITY_LIST_URL =
      SERVER + "/json.htm?type=devices&filter=utility&used=true&order=Name";
  private static final String DOMOTICZ_SWITCH_URL =
      SERVER + "/json.htm?type=command&param=switchlight&idx=%s&switchcmd=%s";
  private static final String DOMOTICZ_SET_SETPOINT =
      SERVER + "/json.htm?type=command&param=setsetpoint&idx=%s&setpoint=%s";
  private static final String DOMOTICZ_SET_TEMP_MODE =
      SERVER + "/json.htm?type=setused&idx=%s&tmode=%s&protected=false&used=true";

  private Domoticz domoticz;

  @Override
  public void onSessionStarted(final SessionStartedRequest request, final Session session)
      throws SpeechletException {
    logInfo("onSessionStarted requestId={}, sessionId={}", request.getRequestId(),
        session.getSessionId());

    // any initialization logic goes here
  }

  @Override
  public SpeechletResponse onLaunch(final LaunchRequest request, final Session session)
      throws SpeechletException {
    logInfo("onLaunch requestId={}, sessionId={}", request.getRequestId(), session.getSessionId());

    String speechOutput = "The house is here to help. What can I help you with?";
    // If the user either does not reply to the welcome message or says
    // something that is not understood, they will be prompted again with this text.
    String repromptText = "For instructions on what you can say, please say help me.";

    // Here we are prompting the user for input
    return newAskResponse(speechOutput, repromptText);
  }

  @Override
  public SpeechletResponse onIntent(final IntentRequest request, final Session session)
      throws SpeechletException {
    logInfo("onIntent requestId={}, sessionId={}", request.getRequestId(), session.getSessionId());

    Intent intent = request.getIntent();
    String intentName = (intent != null) ? intent.getName() : null;

    ServerInfo server = new ServerInfo();
    server.setRemoteServerUsername(USERNAME);
    server.setRemoteServerPassword(PASSWORD);
    server.setRemoteServerUrl(HOSTNAME);
    server.setRemoteServerPort(PORT);
    server.setRemoteServerSecure(false);
    server.setEnabled(true);
    server.setRemoteServerAuthenticationMethod(
        Domoticz.Authentication.Method.AUTH_METHOD_BASIC_AUTHENTICATION);
    server.setLocalSameAddressAsRemote();

    domoticz = new Domoticz(server);

    if ("SwitchIntent".equals(intentName)) {
      return getSwitch(intent);
    } else if ("TemperatureIntent".equals(intentName)) {
      return getTemperature(intent);
    } else if ("ThermostatIntent".equals(intentName)) {
      return getThermostat(intent);
    } else if ("AMAZON.HelpIntent".equals(intentName)) {
      return getHelp();
    } else if ("AMAZON.StopIntent".equals(intentName)) {
      PlainTextOutputSpeech outputSpeech = new PlainTextOutputSpeech();
      outputSpeech.setText("Goodbye");
      return SpeechletResponse.newTellResponse(outputSpeech);
    } else if ("AMAZON.CancelIntent".equals(intentName)) {
      PlainTextOutputSpeech outputSpeech = new PlainTextOutputSpeech();
      outputSpeech.setText("Goodbye");
      return SpeechletResponse.newTellResponse(outputSpeech);
    } else {
      throw new SpeechletException("Invalid Intent");
    }
  }

  @Override
  public void onSessionEnded(final SessionEndedRequest request, final Session session)
      throws SpeechletException {
    logInfo("onSessionEnded requestId={}, sessionId={}", request.getRequestId(),
        session.getSessionId());

    // any cleanup logic goes here
  }

  /**
   * Creates a {@code SpeechletResponse} for the SwitchIntent.
   *
   * @param intent intent for the request
   * @return SpeechletResponse spoken and visual response for the given intent
   */
  private SpeechletResponse getSwitch(Intent intent) {
    final Slot switchSlot = intent.getSlot(SWITCH_SLOT);
    final Slot stateSlot = intent.getSlot(STATE_SLOT);

    final PlainTextOutputSpeech outputSpeech = new PlainTextOutputSpeech();
    final SimpleCard card = new SimpleCard();

    domoticz.getDevices(new DevicesReceiver() {

      @Override
      public void onReceiveDevices(ArrayList<DevicesInfo> devices) {
        if (slotHasValue(switchSlot)) {
          final String switchName = switchSlot.getValue();
          DevicesInfo selectedSwitch = null;
          for (DevicesInfo switchInfo : devices) {
            if (switchInfo.getName().equalsIgnoreCase(switchName)) {
              selectedSwitch = switchInfo;
              break;
            }
          }

          if (selectedSwitch == null) {
            String outputSpeechString =
                "I'm sorry, I can't find a switch with that name.  You can ask me for a list of switches.";
            card.setTitle("Switch unknown");
            card.setContent(outputSpeechString);
            return;
          }

          if (slotHasValue(stateSlot)) {
            final String stateName = stateSlot.getValue();

            String outputSpeechString = "Turning the " + switchName;
            int actionVal = 0;
            if ("on".equals(stateName)) {
              outputSpeechString += " on";
              actionVal = DomoticzValues.Device.Switch.Action.ON;
            } else if ("off".equals(stateName)) {
              outputSpeechString += " off";
              actionVal = DomoticzValues.Device.Switch.Action.OFF;
            } else {
              // unknown state
              logInfo('|' + stateName + '|');
              outputSpeechString = "I don't know what state you mean, on or off.";
              outputSpeech.setText(outputSpeechString);
              card.setTitle("State of " + switchName);
              card.setContent(stateName);
              return;
            }

            final String finalizedOutputSpeechString = outputSpeechString;
            domoticz.setAction(selectedSwitch.getIdx(), DomoticzValues.Json.Url.Set.SWITCHES,
                actionVal, 0, PASSWORD, new setCommandReceiver() {
                  @Override
                  public void onReceiveResult(String result) {
                    outputSpeech.setText(finalizedOutputSpeechString);
                    card.setTitle("State of " + switchName);
                    card.setContent(stateName);
                    return;
                  }

                  @Override
                  public void onError(Exception error) {}
                });
          } else {
            // no state value provided, report the state
            String outputSpeechString;
            if (switchName.substring(switchName.length() - 1).equals("s")) {
              outputSpeechString = switchName + " are " + selectedSwitch.getData();
            } else {
              outputSpeechString = switchName + " is " + selectedSwitch.getData();
            }
            outputSpeech.setText(outputSpeechString);
            card.setTitle("State of " + switchName);
            card.setContent(outputSpeechString);
            return;
          }
        } else {
          String outputSpeechString = "The house has the following switches: ";
          List<String> switchList = new ArrayList<String>();
          for (DevicesInfo switchInfo : devices) {
            switchList.add(switchInfo.getName());
          }
          String switchListString = StringUtils.join(switchList, ", ");
          outputSpeechString += switchListString;
          outputSpeech.setText(outputSpeechString);
          card.setTitle("Available switches");
          card.setContent(switchListString);
          return;
        }
      }

      @Override
      public void onError(Exception error) {}

      @Override
      public void onReceiveDevice(DevicesInfo mDevicesInfo) {}
    }, 0, null);
    return SpeechletResponse.newTellResponse(outputSpeech, card);
  }

  private SpeechletResponse getTemperature(Intent intent) {
    Slot tempSensorSlot = intent.getSlot(TEMPSENSOR_SLOT);

    PlainTextOutputSpeech outputSpeech = new PlainTextOutputSpeech();
    SimpleCard card = new SimpleCard();
    String outputSpeechString;

    if (slotHasValue(tempSensorSlot)) {
      String tempSensor = tempSensorSlot.getValue();
      ReadContext ctx = JsonPath.parse(queryDomoticz(DOMOTICZ_TEMP_LIST_URL));
      logInfo("looking for temperature of sensor: " + tempSensor);
      String tempValue = readJsonPath(ctx, "$.result[?(@.Name =~ /" + tempSensor + "/i)].Temp");
      if (tempValue == null) {
        outputSpeechString =
            "I'm sorry, I can't find a temperature sensor with that name. You can ask me for a list of temperature sensors.";
      } else {
        outputSpeechString = "The " + tempSensor + " temperature is ";
        logInfo("Recieved temp: " + tempValue);
        int temperature = (int) Math.round(Double.parseDouble(tempValue));
        logInfo("Parsed temp: " + temperature);
        outputSpeechString += String.valueOf(temperature) + " degrees";
      }
      outputSpeech.setText(outputSpeechString);
      card.setTitle("Temperature");
      card.setContent(outputSpeechString);
      return SpeechletResponse.newTellResponse(outputSpeech, card);
    } else {
      outputSpeechString = "The house has the following temperature sensors: ";
      ReadContext ctx = JsonPath.parse(queryDomoticz(DOMOTICZ_TEMP_LIST_URL));
      List<String> tempSensorList = ctx.read("$.result..Name");
      String tempListString = StringUtils.join(tempSensorList, ", ");
      outputSpeechString += tempListString;
      outputSpeech.setText(outputSpeechString);
      card.setTitle("Available temperature sensors");
      card.setContent(tempListString);
      return SpeechletResponse.newTellResponse(outputSpeech, card);
    }
  }

  private SpeechletResponse getThermostat(Intent intent) {
    Slot temperatureSlot = intent.getSlot(TEMPERATURE_SLOT);
    Slot changeSlot = intent.getSlot(CHANGE_SLOT);
    Slot setpointSlot = intent.getSlot(THERMOSTAT_SETPOINT);
    Slot modeSlot = intent.getSlot(THERMOSTAT_MODE);
    Slot modeValueSlot = intent.getSlot(THERMOSTAT_MODE_VALUE);
    Slot thermostatSlot = intent.getSlot(THERMOSTAT);

    PlainTextOutputSpeech outputSpeech = new PlainTextOutputSpeech();
    SimpleCard card = new SimpleCard();
    String outputSpeechString;

    ReadContext ctx = JsonPath.parse(queryDomoticz(DOMOTICZ_TEMP_LIST_URL));

    String thermostatName = null;
    String thermostatId = null;
    if (slotHasValue(thermostatSlot)) {
      thermostatName = thermostatSlot.getValue();
    } else if (slotHasValue(setpointSlot)) {
      thermostatName = setpointSlot.getValue();
    } else if (slotHasValue(modeSlot)) {
      thermostatName = modeSlot.getValue();
    }

    logInfo("looking for thermostat setpoint: " + thermostatName);
    thermostatId = readJsonPath(ctx, "$.result[?(@.Name =~ /" + thermostatName + "/i)].idx");
    if (thermostatId == null) {
      outputSpeechString =
          "I'm sorry, I can't find a thermostat with that name.  You can ask me for a list of thermostats.";
      card.setTitle("Thermostat unknown");
      card.setContent(outputSpeechString);
      return SpeechletResponse.newTellResponse(outputSpeech, card);
    }

    if (slotHasValue(temperatureSlot)) {
      int temperature = (int) Math.round(Double.parseDouble(temperatureSlot.getValue()));

      queryDomoticz(DOMOTICZ_SET_SETPOINT, thermostatId, temperature);

      outputSpeechString = "Changing thermostat set point to " + temperature + " degrees";
      outputSpeech.setText(outputSpeechString);
      card.setTitle("Thermostat Set");
      card.setContent(outputSpeechString);
      return SpeechletResponse.newTellResponse(outputSpeech, card);
    } else if (slotHasValue(changeSlot)) {

      String dataSetpointStr =
          readJsonPath(ctx, "$.result[?(@.Name =~ /" + thermostatName + "/i)].Data");
      if (dataSetpointStr == null) {
        outputSpeechString =
            "I'm sorry, I can't find a thermostat with that name.  You can ask me for a list of thermostats.";
        card.setTitle("Thermostat unknown");
        card.setContent(outputSpeechString);
        return SpeechletResponse.newTellResponse(outputSpeech, card);
      }

      dataSetpointStr = dataSetpointStr.replaceAll("\\s[FC]", "");
      int dataSetpoint = (int) Math.round(Double.parseDouble(dataSetpointStr));

      String change = changeSlot.getValue();
      if (change.equalsIgnoreCase("Up")) {
        dataSetpoint = dataSetpoint + 1;
      } else {
        dataSetpoint = dataSetpoint - 1;
      }

      queryDomoticz(DOMOTICZ_SET_SETPOINT, thermostatId, dataSetpoint);

      outputSpeechString = "Thermostat " + change + " to " + dataSetpoint + "degress";
      outputSpeech.setText(outputSpeechString);
      card.setTitle("Thermostat " + change);
      card.setContent(outputSpeechString);
      return SpeechletResponse.newTellResponse(outputSpeech, card);
    } else if (slotHasValue(modeValueSlot)) {
      String modeValue = modeValueSlot.getValue();
      Integer modeId = null;

      String modesList =
          readJsonPath(ctx, "$.result[?(@.Name =~ /" + thermostatName + "/i)].Modes");
      if (modesList == null) {
        outputSpeechString =
            "I'm sorry, I can't find a thermostat with that name.  You can ask me for a list of thermostats.";
        card.setTitle("Thermostat unknown");
        card.setContent(outputSpeechString);
        return SpeechletResponse.newTellResponse(outputSpeech, card);
      }

      String[] modesListSplit = modesList.split(";");
      for (int i = 0; i < modesListSplit.length; i = i + 2) {
        if (modeValue.equalsIgnoreCase(modesListSplit[i + 1])) {
          modeId = Integer.parseInt(modesListSplit[i]);
          break;
        }
      }

      if (modeId != null) {
        queryDomoticz(DOMOTICZ_SET_TEMP_MODE, thermostatId, modeId);
      }

      outputSpeechString = "Thermostat mode changed to " + modeValue;
      outputSpeech.setText(outputSpeechString);
      card.setTitle("Thermostat change mode");
      card.setContent(outputSpeechString);
      return SpeechletResponse.newTellResponse(outputSpeech, card);
    } else {
      // There was no item in the intent so return the help prompt.
      return getHelp();
    }
  }

  /**
   * Creates a {@code SpeechletResponse} for the HelpIntent.
   *
   * @return SpeechletResponse spoken and visual response for the given intent
   */
  private SpeechletResponse getHelp() {
    String speechOutput = "The house is here to help. You can ask a question like, "
        + "what's the temperature in the sunroom? ... or," + "tell me to do something like, "
        + "turn on the fish tank light. ... Now, what can I help you with?";
    String repromptText = "You can say things like, what's the thermostat setting,"
        + " what light switches are available," + " or say exit... Now, what can I help you with?";
    return newAskResponse(speechOutput, repromptText);
  }

  /**
   * Wrapper for creating the Ask response. The OutputSpeech and {@link Reprompt} objects are
   * created from the input strings.
   *
   * @param stringOutput the output to be spoken
   * @param repromptText the reprompt for if the user doesn't reply or is misunderstood.
   * @return SpeechletResponse the speechlet response
   */
  private SpeechletResponse newAskResponse(String stringOutput, String repromptText) {
    PlainTextOutputSpeech outputSpeech = new PlainTextOutputSpeech();
    outputSpeech.setText(stringOutput);

    PlainTextOutputSpeech repromptOutputSpeech = new PlainTextOutputSpeech();
    repromptOutputSpeech.setText(repromptText);
    Reprompt reprompt = new Reprompt();
    reprompt.setOutputSpeech(repromptOutputSpeech);

    return SpeechletResponse.newAskResponse(outputSpeech, reprompt);
  }

  private String queryDomoticz(String url) {
    try {
      URL obj = new URL(url);
      HttpURLConnection con = (HttpURLConnection) obj.openConnection();
      String encoded =
          Base64.getEncoder().encodeToString((USERNAME + ":" + PASSWORD).getBytes("UTF-8"));
      con.setRequestProperty("Authorization", "Basic " + encoded);
      con.setRequestMethod("GET");
      int responseCode = con.getResponseCode();
      logInfo("\nSending 'GET' request to URL : " + url);
      logInfo("Response Code : " + responseCode);

      BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
      String inputLine;
      StringBuffer response = new StringBuffer();

      while ((inputLine = in.readLine()) != null) {
        response.append(inputLine);
      }
      in.close();

      return (response.toString());
    } catch (Exception e) {
      log.error("Caught Exception when calling Domoticz", e);
      return (e.getMessage());
    }
  }

  private void queryDomoticz(String url, Object... args) {
    String formattedUrl = String.format(url, args);
    if (formattedUrl != null) {
      logInfo("Formatted url: ", formattedUrl);
      queryDomoticz(formattedUrl);
    }
  }

  private boolean slotHasValue(Slot slot) {
    return (slot != null) && (StringUtil.isNotBlank(slot.getValue()));
  }

  private <T> T readJsonPath(ReadContext ctx, String path) {
    logInfo("Reading path: " + path);
    List<T> ids = ctx.read(path);
    logInfo("Returned: " + ids + " size: " + ids.size());
    if (ids.size() == 1) {
      return ids.get(0);
    }
    return null;
  }

  private void logInfo(String msg) {
    logInfo(msg, new Object[] {});
  }

  private void logInfo(String msg, Object... o1) {
    if ((o1 != null) && (o1.length > 0)) {
      log.info(msg, o1);
      System.out.printf(msg + "\n", o1);
    } else {
      log.info(msg);
      System.out.println(msg);
    }
  }

  private void logError(String msg, Throwable o1) {
    log.error(msg, o1);
    System.out.println(msg);
    o1.printStackTrace();
  }
}
