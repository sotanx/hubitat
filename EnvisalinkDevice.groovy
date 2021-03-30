import groovy.transform.Field

def version() { 
    return "Envisalink 1.0.0" 
}

metadata {
    definition (
        name: "Envisalink Connection", 
        namespace: "Hubitat", 
        author: "David Hebert", 
        importUrl: ""
    ) 
    {
        capability "Initialize"
		capability "Telnet"

        command "statusReport"
        command "configureAllZones"
        command "createZone", ["Number", "String", "String"]
        command "deleteZone", ["Number"]

        attribute "Status", "string"
        attribute "Codes", "json"
        attribute "LastUsedCodePosition", "string"
        attribute "LastUsedCodeName", "string"

        attribute "CID_Code", "string"
        attribute "CID_Type", "string"
        attribute "CID_Partition", "string"
        attribute "CID_UserZone", "string"
        attribute "CID_DATA", "string"
    }

    preferences {
        input("ip", "text", title: "IP Address",  required: true)
        input("passwd", "text", title: "Password", required: true)
        input("masterCode", "text", title: "Master Code", required: true)
        input "debugEnabled", "bool", required: true, title: "Enabling debug logging"
    }
}

/////////////////////////////////////////////////////////////////////////////
// System
/////////////////////////////////////////////////////////////////////////////

def installed() {
	trace("installed", true);
    initialize()
}

def updated() {
    trace("updated", true);
    initialize()
}

def uninstalled() {
    trace("uninstalled", true);
	unschedule();
    telnetClose();
	removeChildDevices(getChildDevices());
}

def initialize() {
	trace("initialize", false);
    unschedule();
	state.programmingMode = "";
    runEvery5Minutes(poll);
    trace("Poll Rate set at 5 minutes", true);
    runIn(5, "telnetConnection");
}

/////////////////////////////////////////////////////////////////////////////
// Driver commands
/////////////////////////////////////////////////////////////////////////////

def statusReport() {
	trace("StatusReport", true);
	sendTelnetCommand(tpiCommands["StatusReport"])
}

def poll() {
	trace("Polling...", true)
    sendTelnetCommand(tpiCommands["Poll"])
}

def configureAllZones() {
    createZone(1, "DSC Porte principale", "contact");
    createZone(2, "DSC Garage", "contact");
    createZone(3, "DSC Porte patio salon", "contact");
    createZone(4, "DSC Porte patio chambre parents", "contact");
    createZone(5, "DSC MS Salon", "motion");
    createZone(6, "DSC MS Chambre parents", "motion");
    createZone(9, "DSC Fenetre salon 2", "contact");
    createZone(10, "DSC Fenetre Loriane", "contact");
    createZone(11, "DSC Fenetre Aurelie", "contact");
    createZone(12, "DSC MS salon 2", "motion");
    createZone(13, "DSC MS Loriane", "motion");
    createZone(14, "DSC MS Aurelie", "motion");
}

def createZone(zoneId, name, type) {
    deleteZone(zoneId);
	
    if ( (zoneId < 0) || (zoneId > 64) ) {
        traceError "zone id out of range ${zoneId}";
        return;
    }
    
    switch( type ) {
        case "contact":
    		addChildDevice("hubitat", "Virtual Contact Sensor", "${device.deviceNetworkId}_C_${zoneId}", [name: "Contact sensor", isComponent: true, label: name]);
            trace("Contact sensor assigned ${device.deviceNetworkId}_C_${zoneId}", false);
            break;
	    case "motion":
		    addChildDevice("hubitat", "Virtual Motion Sensor", "${device.deviceNetworkId}_M_${zoneId}", [name: "Motion sensor", isComponent: true, label: name]);
		    def newDevice = getChildDevice("${device.deviceNetworkId}_M_${zoneId}");
			newDevice.updateSetting("autoInactive",[type:"enum", value:disabled]);
            trace("Motion sensor Sensor assigned ${device.deviceNetworkId}_M_${zoneId}", false);
            break;
        default:
            traceError("unknown device type ${type}");
            break;
	}
}

def deleteZone(zoneId) {
    def zoneDevice = getZoneDevice("${zoneId}");
    if (zoneDevice) {
        deleteChildDevice(zoneDevice.deviceNetworkId)
    }
}

/////////////////////////////////////////////////////////////////////////////
// Telnet
/////////////////////////////////////////////////////////////////////////////

def telnetConnection(){
	trace("telnetConnection", true)
    telnetClose()
	pauseExecution(5000)
	try {
		telnetConnect([termChars:[13,10]], ip, 4025, null, null)
	} catch(e) {
		trace("telnetConnection: ${e.message}", false)
	}
}

private sendTelnetCommand(String s) {
    s = generateChksum(s);
	return new hubitat.device.HubAction(s, hubitat.device.Protocol.TELNET);
}

private sendTelnetLogin(){
	trace("sendTelnetLogin: ${passwd}", true)
    def cmdToSend = tpiCommands["Login"] + "${passwd}"
    def cmdArray = cmdToSend.toCharArray()
    def cmdSum = 0
    cmdArray.each { cmdSum += (int)it }
    def chkSumStr = DataType.pack(cmdSum, 0x08)
    if(chkSumStr.length() > 2) {
        chkSumStr = chkSumStr[-2..-1]
    } 
    cmdToSend += chkSumStr
	cmdToSend = cmdToSend + "\r\n"
	sendHubCommand(new hubitat.device.HubAction(cmdToSend, hubitat.device.Protocol.TELNET))
}

def telnetStatus(String status){
	traceError("telnetStatus- error: ${status}")
	if (status != "receive error: Stream is closed"){
		traceError("Telnet connection dropped...")
	} else {
		traceError("Telnet is restarting...")
	}
	runOnce(new Date(now() + 10000), telnetConnection)
}

def parse(String message) {
    message = preProcessMessage(message)
    def messageId = tpiResponses[message.take(3) as int];
    switch(messageId) {
        case LOGININTERACTION:
            def subMessageId = tpiResponses[message.take(4) as int];
            switch(subMessageId) {
                case LOGINPROMPT:
                    loginPrompt();
                    break;
                case PASSWORDINCORRECT:
                    traceError(PASSWORDINCORRECT);
                    break;
                case LOGINSUCCESSFUL:
                    trace(LOGINSUCCESSFUL, false);
                    statusReport();
                    break;
                case LOGINTIMEOUT:
                    traceError(LOGINTIMEOUT);
                    break;
            }
            break;

        case COMMANDACCEPTED:
            switch(state.programmingMode) {
                case SETUSERCODESEND:
                    setUserCodeSend();
                    break;
                case SETUSERCODECOMPLETE:
                    setUserCodeComplete();
                    break;
                case DELETEUSERCODE:
                    deleteUserCodeSend();
                    break;
                case DELETEUSERCOMPLETE:
                    deleteUserCodeComplete();
                    break;
            }
            break;

        case SYSTEMERROR:
            systemError(message);
            break;

        case ZONEOPEN:
            zoneOpen(message);
            break;

        case ZONERESTORED:
            zoneClosed(message);
            break;

        case PARTITIONNOTREADYFORCEARMINGENABLED:
            partitionReadyForForcedArmEnabled()
            break;

        case TROUBLELEDON:
        case TROUBLELEDOFF:
        case PARTITIONISBUSY:
        case PARTITIONREADY:
        case PARTITIONNOTREADY:
            def id = message.substring(3, 4) as int;
            trace("${messageId} ${id}", true);
            break;

        case PARTITIONARMEDSTATE:
            if(tpiResponses[message.take(5) as int] == PARTITIONARMEDAWAY) {
                partitionArmed(true)
            }
            if(tpiResponses[message.take(5) as int] == PARTITIONARMEDHOME) {
                partitionArmed(false)
            }
            break;

        case PARTITIONDISARMED:
            partitionDisarmed();
            break;

        case KEYPADLEDSTATE:
            keypadLedState(message.substring(3,message.size()))
            break;

        default:
            trace("Parsing unknown incoming message: [" + message + "] => ${messageId}\n\n", true);
            break;
    }

    /*
    if(tpiResponses[message.take(3) as int] == CODEREQUIRED) {
        composeMasterCode()
    }

    if(tpiResponses[message.take(3) as int] == MASTERCODEREQUIRED) {
        composeMasterCode()
    }

    if(tpiResponses[message.take(3) as int] == INSTALLERSCODEREQUIRED) {
        composeInstallerCode()
    }

    if(tpiResponses[message.take(3) as int] == PARTITIONREADY) {
        partitionReady()
    }

    if(tpiResponses[message.take(3) as int] == PARTITIONNOTREADY) {
        partitionNotReady()
    }

    if(tpiResponses[message.take(3) as int] == PARTITIONINALARM) {
        partitionAlarm()
    }


    if(tpiResponses[message.take(3) as int] == EXITDELAY) {
        exitDelay()
    }

    if(tpiResponses[message.take(3) as int] == ENTRYDELAY) {
        entryDelay()
    }

    if(tpiResponses[message.take(3) as int] == KEYPADLOCKOUT) {
        keypadLockout()
    }

    if(tpiResponses[message.take(3) as int] == USEROPENING){
        partitionArmedNight()
        parseUser(message)
    }

    if(tpiResponses[message.take(3) as int] == USERCLOSING){
        partitionArmedNight()
        parseUser(message)
    }

    if(tpiResponses[message.take(3) as int] == SPECIALCLOSING){
    }

    if(tpiResponses[message.take(3) as int] == SPECIALOPENING){
    }
    */
}

/////////////////////////////////////////////////////////////////////////////
// Message handling
/////////////////////////////////////////////////////////////////////////////

private preProcessMessage(message){
 	message = checkTimeStamp(message)
	message = message.take(message.size() - 2)
	return message
}

private loginPrompt(){
	trace("loginPrompt", true)
	send_Event(name: "DeviceWatch-DeviceStatus", value: "online")
	sendTelnetLogin()
}

private setUserCodeSend(){
	trace("setUserCodeSend", true)
	state.programmingMode = SETUSERCODECOMPLETE
	pauseExecution(3000)
	composeKeyStrokes("#")
}

private setUserCodeComplete(){
	trace("setUserCodeSend", true)
	state.programmingMode = ""
	def storedCodes = new groovy.json.JsonSlurper().parseText(device.currentValue("Codes"))
	assert storedCodes instanceof Map
	def newCodeMap = [name: (state.newName), code: (state.newCode.toString())]
	storedCodes.put((state.newCodePosition.toString()), (newCodeMap))
	trace("storedCodes: ${storedCodes}", true)
	def json = new groovy.json.JsonBuilder(storedCodes)
	send_Event(name:"Codes", value: json, displayed:true, isStateChange: true)
	state.newCode = ""
	state.newCodePosition = ""
	state.name = ""
}

private composeKeyStrokes(data){
	trace("composeKeyStrokes: ${data}", true)
    sendMessage = tpiCommands["SendKeyStroke"]
	sendProgrammingMessage(sendMessage + data)
}

private deleteUserCodeSend(){
	trace("deleteUserCodeSend", true)
	state.programmingMode = DELETEUSERCOMPLETE
	pauseExecution(3000)
	composeKeyStrokes("#")
}

private deleteUserCodeComplete(){
	trace("deleteUserCodeComplete", true)
	state.programmingMode = ""
	def storedCodes = new groovy.json.JsonSlurper().parseText(device.currentValue("Codes"))
	assert storedCodes instanceof Map
	trace("storedCodes: ${storedCodes}", true)
	def selectedCode = storedCodes[state.newCodePosition]
	trace("Selected Code: ${selectedCode}", true)
	storedCodes.remove(state.newCodePosition.toString())
	def json = new groovy.json.JsonBuilder(storedCodes)
	send_Event(name:"Codes", value: json, displayed:true, isStateChange: true)
	state.newCode = ""
	state.newCodePosition = ""
	state.name = ""
}

private systemError(message){
	def substringCount = message.size() - 3
	message = message.substring(4,message.size()).replaceAll('0', '') as int
	traceError("System Error: ${message} - ${errorCodes[(message)]}")
	if (errorCodes[(message)] == "Receive Buffer Overrun"){
		composeKeyStrokes("#")
	}
}

private zoneOpen(message, Boolean autoReset = false){
	def zoneDevice
	def deviceId = message.substring(3,6) as int;
    def myStatus
    trace("ZoneOpen ${deviceId}", true);
	zoneDevice = getZoneDevice("${deviceId}")
	if (zoneDevice){
		if (zoneDevice.capabilities.find { item -> item.name.startsWith('Contact')}) {
            if (zoneDevice.latestValue("contact") != "open") {
                trace("Contact ${deviceId} Open", true)
			    zoneDevice.open()
            }
		} else if (zoneDevice.capabilities.find { item -> item.name.startsWith('Motion')}) {
            if (zoneDevice.latestValue("motion") != "active") {
			    trace("Motion ${deviceId} Active", true)
			    zoneDevice.active()
			    zoneDevice.sendEvent(name: "temperature", value: "", isStateChange: true)
            }
		}
	}
}

private zoneClosed(message){
	def zoneDevice
	def deviceId = message.substring(3,6) as int;
    def myStatus
	trace("ZoneClosed ${deviceId}", true);
    zoneDevice = getZoneDevice("${deviceId}")
	if (zoneDevice){
		trace(zoneDevice, true)
		if (zoneDevice.capabilities.find { item -> item.name.startsWith('Contact')}){
            if (zoneDevice.latestValue("contact") != "closed") {
			    trace("Contact Closed", true)
			    zoneDevice.close()
            }
		} else if (zoneDevice.capabilities.find { item -> item.name.startsWith('Motion')}) {
            if (zoneDevice.latestValue("motion") != "inactive") {
			    trace("Motion Inactive", true)
			    zoneDevice.inactive()
			    zoneDevice.sendEvent(name: "temperature", value: "", isStateChange: true)
            }
		}
	}
}

private partitionReadyForForcedArmEnabled(){
	trace("partitionReadyForForcedArmEnabled", true);
	if (device.currentValue("Status") != PARTITIONNOTREADYFORCEARMINGENABLED) { 
        send_Event(name:"Status", value: PARTITIONNOTREADYFORCEARMINGENABLED, isStateChange: true) 
    }
	if (device.currentValue("contact") != "closed") { 
        send_Event(name:"contact", value: "closed") 
    }
}

private partitionArmed(isAway){
	if (device.currentValue("Status") != PARTITIONARMEDAWAY) { 
        send_Event(name:"Status", value: PARTITIONARMEDAWAY, isStateChange: true); 
    }
	if (device.currentValue("switch") != "on") { 
        send_Event(name:"switch", value: "on", isStateChange: true); 
    }
	if (device.currentValue("contact") != "closed") { 
        send_Event(name:"contact", value: "closed", isStateChange: true);
    }

    if ( isAway == true ) {
	    if (state.armState != "armed_away"){
    		trace("Armed Away", false)
		    state.armState = "armed_away"
            // if (location.hsmStatus != "armedAway") {
            //	sendLocationEvent(name: "hsmSetArm", value: "armAway"); ifDebug("sendLocationEvent(name:\"hsmSetArm\", value:\"armAway\")")
            // }
        }
    } else {
	    if (state.armState != "armed_home"){
		    trace("Armed Home", false)
		    state.armState = "armed_home"
            // if (location.hsmStatus != "armedHome") {
                //log.info "systemArmedHome() hsmStatus=$location.hsmStatus  setting hsmSetArm=armHome"
                //sendLocationEvent(name: "hsmSetArm", value: "armHome"); ifDebug("sendLocationEvent(name:\"hsmSetArm\", value:\"armHome\")")
            // }
        }
    }

    // parent.lockIt()
    // parent.switchItArmed()
    // parent.speakArmed()
}

private partitionDisarmed(){
	trace("partitionDisarmed", false);
    if ((device.currentValue("Status") != PARTITIONDISARMED) && (state.armState != "disarmed")) { 
        send_Event(name:"Status", value: PARTITIONDISARMED, isStateChange: true) 
    }
	if (device.currentValue("switch") != "off") { 
        send_Event(name:"switch", value: "off", isStateChange: true) 
    }
	if (device.currentValue("contact") != "closed") { 
        send_Event(name:"contact", value: "closed", isStateChange: true) 
    }

    if ((state.armState != "disarmed")) {
		trace("disarming", false)
		state.armState = "disarmed"
		// parent.unlockIt()
		// parent.switchItDisarmed()
		// parent.speakDisarmed()
		// if (location.hsmStatus != "disarmed") {
			// sendLocationEvent(name: "hsmSetArm", value: "disarm"); ifDebug("sendLocationEvent(name:\"hsmSetArm\", value:\"disarm\")")
		//}
	}
}

private keypadLedState(ledState){
	def ledBinary = Integer.toBinaryString(hubitat.helper.HexUtils.hexStringToInt(ledState))
	def paddedBinary = ledBinary.padLeft(8, "0");
	trace("keypadLedState ${paddedBinary}", true)
    
    if (ledState == "82" && state.programmingMode == SETUSERCODEINITIALIZE){
		trace("${KEYPADLEDSTATE} ${state.programmingMode}", true);
		state.programmingMode = SETUSERCODESEND;
		composeKeyStrokes(state.newCodePosition + state.newCode);
	}

	if (ledState == "82" && state.programmingMode == DELETEUSERCODEINITIALIZE){
		trace("${KEYPADLEDSTATE} ${state.programmingMode}", true);
		state.programmingMode = DELETEUSERCODE;
		composeKeyStrokes(state.newCodePosition + "*");
	}

	if (paddedBinary.substring(7,8) == "0"){
		trace("Partition Ready LED Off", true);
	}

	if (paddedBinary.substring(7,8) == "1"){
		trace("Partition Ready LED On", true);
	}
}

/////////////////////////////////////////////////////////////////////////////
// Helpers
/////////////////////////////////////////////////////////////////////////////

private getZoneDevice(zoneId) {
	def zoneDevice = null
	zoneDevice = getChildDevice("${device.deviceNetworkId}_${zoneId}")
	if (zoneDevice == null){
		zoneDevice = getChildDevice("${device.deviceNetworkId}_M_${zoneId}")
		if (zoneDevice == null){
			zoneDevice = getChildDevice("${device.deviceNetworkId}_C_${zoneId}")
			if (zoneDevice == null){
				zoneDevice = getChildDevice("${device.deviceNetworkId}_S_${zoneId}")
				if (zoneDevice == null){
					zoneDevice = getChildDevice("${device.deviceNetworkId}_G_${zoneId}")
				}
			}
		}
	}
	return zoneDevice
}

private send_Event(evnt) {
	trace("sendEvent(${evnt})", true)
	sendEvent(evnt)
}

private checkTimeStamp(message){
	if (message =~ timeStampPattern){
		state.timeStampOn = true;
		message = message.replaceAll(timeStampPattern, "")
	} else {
		state.timeStampOn = false;
	}
	return message
}

private generateChksum(String cmdToSend){
	def cmdArray = cmdToSend.toCharArray()
	def cmdSum = 0
	cmdArray.each { cmdSum += (int)it }
	def chkSumStr = DataType.pack(cmdSum, 0x08)
	if(chkSumStr.length() > 2) chkSumStr = chkSumStr[-2..-1]
	cmdToSend += chkSumStr
	cmdToSend
}

private traceError(msg){
	log.error msg
}

def trace(message, debug) {
    if (debug == true) {
        if (debugEnabled == true) { 
            log.debug message
        }        
    } else {
        log.info message
    }
}

/////////////////////////////////////////////////////////////////////////////
// Constants
/////////////////////////////////////////////////////////////////////////////

@Field String timeStampPattern = ~/^\d{2}:\d{2}:\d{2} /

@Field final Map 	errorCodes = [
	0: 	"No Error",
	1: 	"Receive Buffer Overrun",
	2: 	"Receive Buffer Overflow",
	3: 	"Transmit Buffer Overflow",
	10: "Keybus Transmit Buffer Overrun",
	11: "Keybus Transmit Time Timeout",
	12: "Keybus Transmit Mode Timeout",
	13: "Keybus Transmit Keystring Timeout",
	14: "Keybus Interface Not Functioning (the TPI cannot communicate with the security system)",
	15: "Keybus Busy (Attempting to Disarm or Arm with user code)",
	16: "Keybus Busy - Lockout (The panel is currently in Keypad Lockout - too many disarm attempts)",
	17: "Keybus Busy - Installers Mode (Panel is in installers mode, most functions are unavailable)",
	18: "Keybus Busy - General Busy (The requested partition is busy)",
	20: "API Command Syntax Error",
	21: "API Command Partition Error (Requested Partition is out of bounds)",
	22: "API Command Not Supported",
	23: "API System Not Armed (sent in response to a disarm command)",
	24: "API System Not Ready to Arm (system is either not-secure, in exit-delay, or already armed",
	25: "API Command Invalid Length 26 API User Code not Required",
	26: "API User Code not Required",
	27: "API Invalid Characters in Command (no alpha characters are allowed except for checksum"
]

@Field static final String COMMANDACCEPTED = "Command Accepted"
@Field static final String KEYPADLEDSTATE = "Keypad LED State"
@Field static final String PROGRAMMINGON = "Keypad LED State ON"
@Field static final String PROGRAMMINGOFF = "Keypad LED State OFF"
@Field static final String COMMANDERROR = "Command Error"
@Field static final String SYSTEMERROR = "System Error"
@Field static final String LOGININTERACTION = "Login Interaction"
@Field static final String LEDFLASHSTATE = "Keypad LED FLASH state"
@Field static final String TIMEDATEBROADCAST = "Time - Date Broadcast"
@Field static final String RINGDETECTED = "Ring Detected"
@Field static final String INDOORTEMPBROADCAST = "Indoor Temp Broadcast"
@Field static final String OUTDOORTEMPBROADCAST = "Outdoor Temperature Broadcast"
@Field static final String ZONEALARM = "Zone Alarm"
@Field static final String ZONE1ALARM = "Zone 1 Alarm"
@Field static final String ZONE2ALARM = "Zone 2 Alarm"
@Field static final String ZONE3ALARM = "Zone 3 Alarm"
@Field static final String ZONE4ALARM = "Zone 4 Alarm"
@Field static final String ZONE5ALARM = "Zone 5 Alarm"
@Field static final String ZONE6ALARM = "Zone 6 Alarm"
@Field static final String ZONE7ALARM = "Zone 7 Alarm"
@Field static final String ZONE8ALARM = "Zone 8 Alarm"
@Field static final String ZONEALARMRESTORE = "Zone Alarm Restored"
@Field static final String ZONE1ALARMRESTORE = "Zone 1 Alarm Restored"
@Field static final String ZONE2ALARMRESTORE = "Zone 2 Alarm Restored"
@Field static final String ZONE3ALARMRESTORE = "Zone 3 Alarm Restored"
@Field static final String ZONE4ALARMRESTORE = "Zone 4 Alarm Restored"
@Field static final String ZONE5ALARMRESTORE = "Zone 5 Alarm Restored"
@Field static final String ZONE6ALARMRESTORE = "Zone 6 Alarm Restored"
@Field static final String ZONE7ALARMRESTORE = "Zone 7 Alarm Restored"
@Field static final String ZONE8ALARMRESTORE = "Zone 8 Alarm Restored"
@Field static final String ZONETAMPER = "Zone Tamper"
@Field static final String ZONETAMPERRESTORE = "Zone Tamper RestoreD"
@Field static final String ZONEFAULT = "Zone Fault"
@Field static final String ZONEFAULTRESTORED = "Zone Fault RestoreD"
@Field static final String ZONEOPEN = "Zone Open"
@Field static final String ZONERESTORED = "Zone Restored"
@Field static final String TIMERDUMP = "Envisalink Zone Timer Dump"
@Field static final String BYPASSEDZONEBITFIELDDUMP = "Bypassed Zones Bitfield Dump"
@Field static final String DURESSALARM = "Duress Alarm"
@Field static final String FKEYALARM = "Fire Key Alarm"
@Field static final String FKEYRESTORED = "Fire Key Restored"
@Field static final String AKEYALARM = "Aux Key Alarm"
@Field static final String AKEYRESTORED = "Aux Key Restored"
@Field static final String PKEYALARM = "Panic Key Alarm"
@Field static final String PKEYRESTORED = "Panic Key Restored"
@Field static final String TWOWIRESMOKEAUXALARM = "2-Wire Smoke Aux Alarm"
@Field static final String TWOWIRESMOKEAUXRESTORED = "2-Wire Smoke Aux Restored"
@Field static final String PARTITIONREADY = "Partition is ready"
@Field static final String PARTITIONNOTREADY = "Partition is not Ready"
@Field static final String PARTITIONARMEDSTATE = "Armed State"
@Field static final String PARTITIONARMEDAWAY = "Armed Away"
@Field static final String PARTITIONARMEDHOME = "Armed Home"
@Field static final String PARTITIONARMEDNIGHT = "Armed Night"
@Field static final String PARTITIONNOTREADYFORCEARMINGENABLED = "Partition Ready - Force Arming Enabled"
@Field static final String PARTITIONINALARM = "In Alarm"
@Field static final String PARTITIONDISARMED = "Disarmed"
@Field static final String EXITDELAY = "Exit Delay in Progress"
@Field static final String ENTRYDELAY = "Entry Delay in Progress"
@Field static final String KEYPADLOCKOUT = "Keypad Lock-out"
@Field static final String PARTITIONFAILEDTOARM = "Partition Failed to Arm"
@Field static final String PFMOUTPUT = "PFM Output is in Progress"
@Field static final String CHIMEENABLED = "Chime Enabled"
@Field static final String CHIMEDISABLED = "Chime Disabled"
@Field static final String INVALIDACCESSCODE = "Invalid Access Code"
@Field static final String FUNCTIONNOTAVAILABLE = "Function Not Available"
@Field static final String FAILURETOARM = "Failure to Arm"
@Field static final String PARTITIONISBUSY = "Partition is busy"
@Field static final String SYSTEMARMINGPROGRESS = "System Arming Progress"
@Field static final String SYSTEMININSTALLERSMODE = "System in Installers Mode"
@Field static final String USERCLOSING = "User Closing"
@Field static final String SPECIALCLOSING = "Special Closing"
@Field static final String PARTIALCLOSING = "Partial Closing"
@Field static final String USEROPENING = "User Opening"
@Field static final String SPECIALOPENING = "Special Opening"
@Field static final String PANELBATTERYTROUBLE = "Panel Battery Trouble"
@Field static final String PANELBATTERYTROUBLERESTORED = "Panel Battery Trouble Restored"
@Field static final String PANELACTROUBLE = "Panel AC Trouble"
@Field static final String PANELACTROUBLERESTORED = "Panel AC Trouble Restored"
@Field static final String SYSTEMBELLTROUBLE = "System Bell Trouble"
@Field static final String SYSTEMBELLTROUBLERESTORED = "System Bell Trouble Restored"
@Field static final String FTCTROUBLE = "FTC Trouble"
@Field static final String FTCTROUBLERESTORED = "FTC Trouble Restored"
@Field static final String BUFFERNEARFULL = "Buffer Near Full"
@Field static final String GENERALSYSTEMTAMPER = "General System Tamper"
@Field static final String GENERALSYSTEMTAMPERRESTORED = "General System Tamper Restored"
@Field static final String TROUBLELEDON = "Trouble LED ON"
@Field static final String TROUBLELEDOFF = "Trouble LED OFF"
@Field static final String FIRETROUBLEALARM = "Fire Trouble Alarm"
@Field static final String FIRETROUBLEALARMRESTORED = "Fire Trouble Alarm Restored"
@Field static final String VERBOSETROUBLESTATUS = "Verbose Trouble Status"
@Field static final String CODEREQUIRED = "Code Required"
@Field static final String COMMANDOUTPUTPRESSED = "Command Output Pressed"
@Field static final String MASTERCODEREQUIRED = "Master Code Required"
@Field static final String INSTALLERSCODEREQUIRED = "Installers Code Required"
@Field static final String PASSWORDINCORRECT = "TPI Login password required"
@Field static final String LOGINSUCCESSFUL = "Login Successful"
@Field static final String LOGINTIMEOUT = "Time out.  You did not send password within 10 seconds"
@Field static final String APIFAULT = "API Command Syntax Error"
@Field static final String LOGINPROMPT = "Send Login"

@Field static final String SETUSERCODEINITIALIZE = "SETUSERCODEINITIALIZE"
@Field static final String SETUSERCODESEND = "SETUSERCODESEND"
@Field static final String SETUSERCODECOMPLETE = "SETUSERCODECOMPLETE"

@Field static final String DELETEUSERCODEINITIALIZE = "DELETEUSERCODEINITIALIZE"
@Field static final String DELETEUSERCODE = "DELETEUSERCODE"
@Field static final String DELETEUSERCOMPLETE = "DELETEUSERCOMPLETE"

@Field final Map 	tpiResponses = [
    500: COMMANDACCEPTED,
    501: COMMANDERROR,
    502: SYSTEMERROR,
	502020: APIFAULT,
    505: LOGININTERACTION,
    5050: PASSWORDINCORRECT,
    5051: LOGINSUCCESSFUL,
    5052: LOGINTIMEOUT,
    5053: LOGINPROMPT,
    510: KEYPADLEDSTATE,
    51091: PROGRAMMINGON,
    51090: PROGRAMMINGOFF,
    511: LEDFLASHSTATE,
    550: TIMEDATEBROADCAST,
    560: RINGDETECTED,
    561: INDOORTEMPBROADCAST,
    562: OUTDOORTEMPBROADCAST,
    601: ZONEALARM,
	6011001: ZONE1ALARM,
	6011002: ZONE2ALARM,
	6011003: ZONE3ALARM,
	6011004: ZONE4ALARM,
	6011005: ZONE5ALARM,
	6011006: ZONE6ALARM,
	6011007: ZONE7ALARM,
	6011008: ZONE8ALARM,
    602: ZONEALARMRESTORE,
	6021001: ZONE1ALARMRESTORE,
	6021002: ZONE2ALARMRESTORE,
	6021003: ZONE3ALARMRESTORE,
	6021004: ZONE4ALARMRESTORE,
	6021005: ZONE5ALARMRESTORE,
	6021006: ZONE6ALARMRESTORE,
	6021007: ZONE7ALARMRESTORE,
	6021008: ZONE8ALARMRESTORE,
    603: ZONETAMPER,
	604: ZONETAMPERRESTORE,
    605: ZONEFAULT,
    606: ZONEFAULTRESTORED,
    609: ZONEOPEN,
    610: ZONERESTORED,
    615: TIMERDUMP,
    616: BYPASSEDZONEBITFIELDDUMP,
    620: DURESSALARM,
    621: FKEYALARM,
    622: FKEYRESTORED,
    623: AKEYALARM,
    624: AKEYRESTORED,
    625: PKEYALARM,
    626: PKEYRESTORED,
    631: TWOWIRESMOKEAUXALARM,
    632: TWOWIRESMOKEAUXRESTORED,
    650: PARTITIONREADY,
    651: PARTITIONNOTREADY,
	652: PARTITIONARMEDSTATE,
    65210: PARTITIONARMEDAWAY,
	65211: PARTITIONARMEDHOME,
    653: PARTITIONNOTREADYFORCEARMINGENABLED,
    654: PARTITIONINALARM,
    655: PARTITIONDISARMED,
    656: EXITDELAY,
   	657: ENTRYDELAY,
    658: KEYPADLOCKOUT,
    659: PARTITIONFAILEDTOARM,
    660: PFMOUTPUT,
   	663: CHIMEENABLED,
    664: CHIMEDISABLED,
    670: INVALIDACCESSCODE,
    671: FUNCTIONNOTAVAILABLE,
    672: FAILURETOARM,
    673: PARTITIONISBUSY,
    674: SYSTEMARMINGPROGRESS,
    680: SYSTEMININSTALLERSMODE,
    700: USERCLOSING,
    701: SPECIALCLOSING,
    702: PARTIALCLOSING,
   	750: USEROPENING,
    751: SPECIALOPENING,
    800: PANELBATTERYTROUBLE,
    801: PANELBATTERYTROUBLERESTORED,
    802: PANELACTROUBLE,
   	803: PANELACTROUBLERESTORED,
    806: SYSTEMBELLTROUBLE,
    807: SYSTEMBELLTROUBLERESTORED,
    814: FTCTROUBLE,
    815: FTCTROUBLERESTORED,
    816: BUFFERNEARFULL,
    829: GENERALSYSTEMTAMPER,
    830: GENERALSYSTEMTAMPERRESTORED,
    840: TROUBLELEDON,
    841: TROUBLELEDOFF,
    842: FIRETROUBLEALARM,
    843: FIRETROUBLEALARMRESTORED,
    849: VERBOSETROUBLESTATUS,
    900: CODEREQUIRED,
    912: COMMANDOUTPUTPRESSED,
    921: MASTERCODEREQUIRED,
    922: INSTALLERSCODEREQUIRED
]

@Field final Map tpiCommands = [
    Login: "005",
    Poll: "000",
    TimeStampOn: "0550",
    TimeStampOff: "0551",
    StatusReport: "001",
    Disarm: "0401",
    ToggleChime: "0711*4",
    ArmHome: "0311",
    ArmAway: "0301",
    ArmNight: "0321",
    ArmAwayZeroEntry: "0321",
    PanicFire: "0601",
    PanicAmbulance: "0602",
    PanicPolice: "0603",
    CodeSend: "200",
    EnterUserCodeProgramming: "0721", 
    SendKeyStroke: "0711"
]