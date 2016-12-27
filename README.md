# alexa_domoticz_bridge
Amazon Echo Alexa to domoticz bridge

[_Demo on youtube._](https://www.youtube.com/watch?v=BCLQecMM_tg)

# Instructions

### Server side
* _Where?_ This code will either run on your Domoticz box or within AWS Lambda. If you are running it locally on Domoticz you need to have SSL HTTP port redirected from your public facing firewall and use a HTTPS cert that is properly signed. You have been warned, but [read up on how the web service works](https://developer.amazon.com/public/solutions/alexa/alexa-skills-kit/docs/developing-an-alexa-skill-as-a-web-service) and what precautions are taken to verify Amazon as the source of requests.  Running it in Lambda allows you to get past the HTTPS requirement
* _What to configure_ Put your device names into LIST_OF_SWITCHES, LIST_OF_THERMOSTATS, LIST_OF_THERMOSTATS_SETPOINTS, LIST_OF_THERMOSTATS_MODES, and LIST_OF_TEMPSENSORS.  Lastly enter all of the possible thermostat mode values into LIST_OF_THERMOSTATS_MODE_VALUES.
* _Rebuild the source._  Build the source with the ./rebuild script.
* _Run the code._ Run with the ./run script only if running locall on Domoticz.

### Amazon Echo 
* _Create a new Alexa Skill on Amazon's Developer site_. Use the same account that is lniked to your Echo for less headaches down the road.
* Put in the contents of the speechAssets folder into the approriate boxes on the skill's Interaction Model page.
* In the Configuration page select if you are running this on Lambda or via HTTPS on your Domoticz box
* In Lambda just make sure you upload the "*jar-with-dependeces.jar" and set the HOSTNAME, PORT, USERNAME and PASSWORD environment variables.  Note the ARN id in Lambda to copy that into the Alexa console.
* Note the Amazon skill id on the AWS console and copy that into DomoticzSpeechletRequestStreamHandler where it says [your id here]
* Once you have setup the skill in test mode it will automatically work with your Echo you have related to your Alexa developer account.  There is no need to ever publish the skill on the marketplace if you are using it for personal use.
* _Enable the Skill & Test_

# Contents
This includes a modified pom.xml and Launcher.java to support the skills.  The pom includes necessary dependencies, e.g. javax.json. Launcher.java calls the speechlet
