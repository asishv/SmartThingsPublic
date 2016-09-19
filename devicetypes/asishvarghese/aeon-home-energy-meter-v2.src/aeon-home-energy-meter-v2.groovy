metadata {
	definition (name: "Aeon Home Energy Meter v2", namespace: "asishvarghese", author: "Asish Varghese") {
		capability "Energy Meter"
		capability "Power Meter"
		capability "Configuration"
		capability "Sensor"
        
        attribute "cost", "string"

		command "reset"

		fingerprint deviceId: "0x2101", inClusters: " 0x70,0x31,0x72,0x86,0x32,0x80,0x85,0x60"
	}

	// simulator metadata
	simulator {
		for (int i = 0; i <= 10000; i += 1000) {
			status "power  ${i} W": new physicalgraph.zwave.Zwave().meterV1.meterReport(
				scaledMeterValue: i, precision: 3, meterType: 4, scale: 2, size: 4).incomingMessage()
		}
		for (int i = 0; i <= 100; i += 10) {
			status "energy  ${i} kWh": new physicalgraph.zwave.Zwave().meterV1.meterReport(
				scaledMeterValue: i, precision: 3, meterType: 0, scale: 0, size: 4).incomingMessage()
		}
	}

	// tile definitions
	tiles {
		valueTile("power", "device.power", decoration: "flat") {
			state(
            "default",
            label:'${currentValue} W',
            foregroundColors:[
                [value: 1, color: "#000000"],
            	[value: 10000, color: "#ffffff"]
            ], 
            foregroundColor: "#000000",
            backgroundColors:[
				[value: "0 Watts", 		color: "#153591"],
				[value: "1000 Watts", 	color: "#1e9cbb"],
				[value: "2000 Watts", 	color: "#90d2a7"],
				[value: "3000 Watts", 	color: "#44b621"],
				[value: "4000 Watts", 	color: "#f1d801"],
				[value: "5000 Watts", 	color: "#d04e00"], 
				[value: "6000 Watts", 	color: "#bc2323"]
				]
                )
		}
		valueTile("energy", "device.energy", decoration: "flat") {
			state "default", label:'${currentValue} kWh'
		}
        valueTile("cost", "device.cost") {
        	state(
        		"default", 
        		label: '${currentValue}', 
        		foregroundColor: "#000000", 
        		backgroundColor: "#ffffff")
        }
		standardTile("reset", "device.energy", inactiveLabel: false, decoration: "flat") {
			state "default", label:'reset kWh', action:"reset"
		}
		standardTile("refresh", "device.power", inactiveLabel: false, decoration: "flat") {
			state "default", label:'', action:"refresh.refresh", icon:"st.secondary.refresh"
		}
		standardTile("configure", "device.power", inactiveLabel: false, decoration: "flat") {
			state "configure", label:'', action:"configuration.configure", icon:"st.secondary.configure"
		}

		main (["power","energy","cost"])
		details(["power","energy","cost","reset","refresh", "configure"])
	}
    
    preferences {
        input "basicChargeStr", "string", title: "Basic Charge", description: "Basic Charge", defaultValue: "7.87" as String
        input "cutoffkWhStr", "string", title: "Cutoff kWh", description: "Cutoff kWh", defaultValue: "500" as String
    	input "kWhCostStr1", "string", title: "\$/kWh before cutoff", description: "\$/kWh before cutoff", defaultValue: "0.087010" as String
        input "kWhCostStr2", "string", title: "\$/kWh after cutoff", description: "\$/kWh after cutoff", defaultValue: "0.105832" as String
    }
}

def parse(String description) {
	def result = null
	def cmd = zwave.parse(description, [0x31: 1, 0x32: 1, 0x60: 3])
	if (cmd) {
		result = createEvent(zwaveEvent(cmd))
	}
	log.debug "Parse returned ${result?.descriptionText}"
	return result
}

def zwaveEvent(physicalgraph.zwave.commands.meterv1.MeterReport cmd) {
    def totalValue
	if (cmd.scale == 0) {
        totalValue = Math.round(cmd.scaledMeterValue * 100) / 100
        BigDecimal costDecimal = 0.0
        BigDecimal basicCharge = 7.87
        BigDecimal cutoffkWh = 500.0
        BigDecimal kWhCost1 = 0.087010
        BigDecimal kWhCost2 = 0.105832
        if (basicChargeStr != null) {
            basicCharge = basicChargeStr as BigDecimal
        }
        if (cutoffkWhStr != null) {
            cutoffkWh = cutoffkWhStr as BigDecimal
        }
        if (kWhCostStr1 != null) {
            kWhCost1 = kWhCostStr1 as BigDecimal
        }
        if (kWhCostStr2 != null) {
            kWhCost2 = kWhCostStr2 as BigDecimal
        }
        if (totalValue < 500) {
            costDecimal = basicCharge + totalValue * kWhCost1
        } else {
        	costDecimal = basicCharge + cutoffkWh * kWhCost1 + (totalValue - cutoffkWh) * kWhCost2
        }
        def costDisplay = String.format("%5.2f",costDecimal)
        state.costDisp = "Cost\n\$"+costDisplay
        sendEvent(name: "cost", value: state.costDisp, unit: "", descriptionText: "Display Cost: \$${costDisp}", displayed: false)
		[name: "energy", value: cmd.scaledMeterValue, unit: "kWh"]
	} else if (cmd.scale == 1) {
		[name: "energy", value: cmd.scaledMeterValue, unit: "kVAh"]
	}
	else {
		[name: "power", value: Math.round(cmd.scaledMeterValue), unit: "W"]
	}
}

def zwaveEvent(physicalgraph.zwave.Command cmd) {
	// Handles all Z-Wave commands we aren't interested in
	[:]
}

def refresh() {
	delayBetween([
		zwave.meterV2.meterGet(scale: 0).format(),
		zwave.meterV2.meterGet(scale: 2).format()
	])
}

def reset() {
	// No V1 available
	return [
		zwave.meterV2.meterReset().format(),
		zwave.meterV2.meterGet(scale: 0).format()
	]
}

def configure() {
	def cmd = delayBetween([
		zwave.configurationV1.configurationSet(parameterNumber: 101, size: 4, scaledConfigurationValue: 4).format(),   // combined power in watts
		zwave.configurationV1.configurationSet(parameterNumber: 111, size: 4, scaledConfigurationValue: 60).format(), // every 5 min
		zwave.configurationV1.configurationSet(parameterNumber: 102, size: 4, scaledConfigurationValue: 8).format(),   // combined energy in kWh
		zwave.configurationV1.configurationSet(parameterNumber: 112, size: 4, scaledConfigurationValue: 30).format(), // every 5 min
		zwave.configurationV1.configurationSet(parameterNumber: 103, size: 4, scaledConfigurationValue: 0).format(),    // no third report
		zwave.configurationV1.configurationSet(parameterNumber: 113, size: 4, scaledConfigurationValue: 6).format() // every 5 min
	])
	log.debug cmd
	cmd
}
