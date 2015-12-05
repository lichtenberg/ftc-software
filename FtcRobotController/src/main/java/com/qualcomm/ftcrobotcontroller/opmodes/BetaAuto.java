
package com.qualcomm.ftcrobotcontroller.opmodes;

import com.qualcomm.ftccommon.DbgLog;
import com.qualcomm.robotcore.eventloop.opmode.OpMode;
import com.qualcomm.robotcore.hardware.ColorSensor;
import com.qualcomm.robotcore.hardware.DcMotor;
import com.qualcomm.robotcore.hardware.DcMotorController;
import com.qualcomm.robotcore.hardware.I2cDevice;
import com.qualcomm.robotcore.hardware.Servo;




public class BetaAuto extends OpMode {

    FtcConfig ftcConfig = new FtcConfig();

    //
    // The member variables below represent the hardware features of the robot.
    // For each of these objects, we will retrieve information from the
    // hardwareMap.   The hardwareMap is the list of devices that you
    // named on the robot controller using the "settings" and "scan" functions
    // of the robot controller app.

    DcMotor motorFrontRight;
    DcMotor motorFrontLeft;
    DcMotor motorBackRight;
    DcMotor motorBackLeft;
    ColorSensor colorSensorB;
    ColorSensor colorSensorL;
    I2cDevice gyro;
    Servo fingerLeft;
    Servo fingerRight;
    Servo personDropper;
    Servo zipServo;
    //
    // The member variables used in servos
    //
    final static double BUTTON_PRESSED = 0.0;
    final static double BUTTON_NOTPRESSED = 0.6;
    final static double ZIP_CLOSED = 1.0;

    //
    // The member variables below are used for when we are executing a turn.
    //
    int destHeading;
    int degreesToTurn;
    boolean shouldTurnLeft;
    boolean gyroTurnIsRunning = false;
    ModernGyroReader gyroReader;

    //
    // The member variables below are used when we are following a line.
    //
    int blackBaseLine = 0;
    boolean lineDetected = false;
    boolean lineFollowerIsRunning = false;

    //
    // The member variables below are used when we are detecting the beacon and pushing the button.
    //
    boolean beaconLeftIsBlue = false;
    int detectBlueStreak = 0;
    int detectRedStreak = 0;

    //
    // The member variables used for delaying
    //
    boolean delayIsRunning = false;
    double startDelayTime;
    double stopDelayTime;

    //
    // The member variables below describe the drive train of the motor
    //
    double wheelDiameter = 6;     // Wheel diameter in inches. //7641 Bot 6, Mentor Bot 3, was 2.75
    double gearReduction = 2;     // Amount of gear reduction in drive train //7641 Bot 2, Mentor Bot 1, was 1.0
    double encoderCountsPerRevolution = 1120; // This is the number of encoder counts per turn of the output shaft. Do not change.

    boolean moveDistanceIsRunning = false;

    // enum has been moved to bottom of code for easy editing

    /**
     * Constructor
     */
    public BetaAuto() {

    }

    /*
     * Code to run when the op mode is first enabled goes here
     * 
     * @see com.qualcomm.robotcore.eventloop.opmode.OpMode#start()
     */

    @Override
    public void init() {
		/*
		 * Use the hardwareMap to get the dc motors and servos by name. Note
		 * that the names of the devices must match the names used when you
		 * configured your robot and created the configuration file.
		 */

        ftcConfig.init(hardwareMap.appContext, this); // Do not comment out
		
		/*
		 *   Gather motors out of the hardware map.
		 */
        motorFrontRight = hardwareMap.dcMotor.get("motor-1");
        motorFrontLeft = hardwareMap.dcMotor.get("motor-2");
        motorBackRight = hardwareMap.dcMotor.get("motor-3");
        motorBackLeft = hardwareMap.dcMotor.get("motor-4");
        motorFrontRight.setDirection(DcMotor.Direction.REVERSE);
        motorBackLeft.setDirection(DcMotor.Direction.REVERSE);

        /*
         * Servos
         */
        fingerRight = hardwareMap.servo.get("servo-right");
        fingerLeft = hardwareMap.servo.get("servo-left");
        personDropper = hardwareMap.servo.get("servo-person");
        zipServo = hardwareMap.servo.get("servo-zip");
        fingerRight.setDirection(Servo.Direction.FORWARD);
        fingerLeft.setDirection(Servo.Direction.REVERSE);

        /*
         * Gather the sensors out of the hardware map.
         */
        colorSensorB = hardwareMap.colorSensor.get("color-front");
        colorSensorL = hardwareMap.colorSensor.get("color-bottom");
        colorSensorL.setI2cAddress(0x3E);

        /*
         * Set up our gyro.   To help with the gyro we take the I2C device
         * and give it to our special ModernGyroReader class, which takes care
         * of the dirty work of reading the heading out of the gyro.
         */

        gyro = hardwareMap.i2cDevice.get("gyro-1");
        gyroReader = new ModernGyroReader(gyro);

        /*
         * Set up the color sensor.
         */

        //colorSensorB.enableLed(true); // nope. don't enable the LED
        colorSensorL.enableLed(true);


    }

    //
    // normalizeHeading(heading)
    //
    // Force a heading to fit within the range 0..359.   For example,
    // if the heading is 370 degrees, that's really the same as 10 degrees.
    // If the heading is -10 degrees, that's really 350.
    //

    private int normalizeHeading(int heading) {
        while (heading < 0) heading += 360;
        while (heading >= 360) heading -= 360;
        return heading;
    }

    //
    // subtractHeadings(a,b)
    //
    // Subtract two compass headings, returning the difference between them in degrees.
    //
    // This routine tries to be smart about the direction, so it will pick the smaller of
    // the two differences (turning left 10 vs right 350).
    //

    private int subtractHeadings(int a, int b) {
        int difference;

        difference = a - b;

        if (difference > 180) return -(360 - difference);
        if (difference <= -180) return (360 + difference);

        return difference;
    }

    //
    // inchesToEncoder(inches)
    //
    // Given some distance in inches, compute the amount that the encoders will change after the robot
    // has moved that distance.
    //
    // To do this, we need the following info:
    //    - The diameter of the wheel
    //    - The ratio of the gears between the motor's output shaft and the wheel
    //    - The number of encoder counts for each revolution of the output shaft.
    //
    // For example, suppose the wheel is 2" in diameter and the encoder counts 1120 pulses per revolution.
    // Also, imagine that you have a 2:1 reduction using a chain or gears.
    // This means that you need 1120 * 2 = 2240 pulses to rotate the wheel one turn.
    // Since the wheel's diameter is 2", this means that the circumference is 2*pi = 6.28".
    // If you want to move 12 inches, you would need the wheels to turn 1.91 times.
    // 1.91 rotations is 1.91 * 1120 * 2 encoder counts = 4274.
    //

    private int inchesToEncoder(double inches) {
        double wheelCircumference = Math.PI * wheelDiameter;
        double encoderCounts;

        encoderCounts = (inches / wheelCircumference) * (encoderCountsPerRevolution * gearReduction);

        return (int) encoderCounts;
    }

    //
    // The init_loop routine is called repeatedly in between the time you press the INIT
    // button and the time you press the START button.
    //
    // For our example, we will read the optical distance (line follow) sensor
    // and remember its maximum value.  This should be the color of the black
    // carpet tiles, so that we can easily tell the difference between the black
    // tile and the white line.
    //

    @Override
    public void init_loop() {
        int rawSensorValue;

        // After the INIT button is pressed, this routine gets called over and over
        // in a loop.

        ftcConfig.init_loop(hardwareMap.appContext, this);

        rawSensorValue = colorSensorL.alpha();
        if (rawSensorValue > blackBaseLine) {
            blackBaseLine = rawSensorValue;
        }

        //
        // Add some telemetry.   The FTC forum says you should be able to read
        // the joystick values here, but I have not been able to do so.
        //
        telemetry.addData("baseline", blackBaseLine);
        telemetry.addData("buttons", (gamepad1.x ? "X" : "") + (gamepad1.y ? "Y" : ""));

        //
        // Keep reading the gyro all the time.
        //

        gyroReader.checkGyro();
    }

    //
    // This is the line sensor follower step.
    //

    public boolean lineFollower() {
        //
        // If we have not detected the line yet, keep going
        // at 0.25 power.   Once we see the white line,
        // set lineDetected true and start following.

        if (lineFollowerIsRunning) {

            int lineSensor = colorSensorL.alpha(); // Get the amount of light detected by the sensor as an int
            //int lineSensor = distanceSensor.getLightDetectedRaw(); // Get the amount of light detected by the sensor as an int

            if (lineSensor > (blackBaseLine + 2)) { // TODO change this value
                if (!lineDetected) {
                    DbgLog.msg("LINE_FOLLOW:  Detected the line");
                    int distanceInEncoderCounts = inchesToEncoder(18);
                    motorFrontLeft.setTargetPosition(motorFrontLeft.getCurrentPosition() + distanceInEncoderCounts);
                    motorFrontRight.setTargetPosition(motorFrontRight.getCurrentPosition() + distanceInEncoderCounts);
                    lineDetected = true;
                }
            }
            if (!lineDetected) {
                // Line not detected yet. Keep going straight.
                motorFrontLeft.setPower(0.10);
                motorFrontRight.setPower(0.10);
                motorBackRight.setPower(0.10);
                motorBackLeft.setPower(0.10);
            } else if (motorFrontRight.getCurrentPosition() >= motorFrontRight.getTargetPosition()) {
                motorFrontLeft.setPower(0);
                motorFrontRight.setPower(0);
                motorBackLeft.setPower(0);
                motorBackRight.setPower(0);
                // drop the climbers
                personDropper.setPosition(0);
                //while(delay(0.1)) {}
                personDropper.setPosition(0.8);
                motorFrontRight.setMode(DcMotorController.RunMode.RUN_WITHOUT_ENCODERS);
                motorFrontLeft.setMode(DcMotorController.RunMode.RUN_WITHOUT_ENCODERS);
                lineFollowerIsRunning = false;
            } else {
                // Line has been detected.   If we see the line, turn one way.
                // If we do not see the line, turn the other way.
                // The robot will oscillate as it follows the line.
                if (pushButton) pushTheButton(); // pushButton is from ftcConfig settings
                if (lineSensor > (blackBaseLine + 2)) { // light detected
                    lineDetected = true;
                    motorFrontLeft.setPower(0);
                    motorFrontRight.setPower(0.10);
                    motorBackLeft.setPower(0);
                    motorBackRight.setPower(0.10);
                } else {
                    motorFrontLeft.setPower(0.10);
                    motorFrontRight.setPower(0);
                    motorBackLeft.setPower(0.10);
                    motorBackRight.setPower(0);
                }
            }
        } else {
            motorFrontRight.setMode(DcMotorController.RunMode.RUN_USING_ENCODERS);
            motorFrontLeft.setMode(DcMotorController.RunMode.RUN_USING_ENCODERS);
            lineDetected = false;
            lineFollowerIsRunning = true;
        }
        return lineFollowerIsRunning;
    }

    public void pushTheButton() {
        // Determines if the robot should push the left button or the right one
        // and extends the corresponding finger
        if (ifPushLeftButton() && pushButton) {
            if (fingerLeft.getPosition() > 0.55) {
                fingerLeft.setPosition(BUTTON_PRESSED);
            }
            if (fingerRight.getPosition() < 0.05) {
                fingerRight.setPosition(BUTTON_NOTPRESSED);
            }
        } else if (!ifPushLeftButton() && pushButton){
            if (fingerLeft.getPosition() < 0.05) {
                fingerLeft.setPosition(BUTTON_NOTPRESSED);
            }
            if (fingerRight.getPosition() > 0.55) {
                fingerRight.setPosition(BUTTON_PRESSED);
            }
        } else {
            fingerLeft.setPosition(BUTTON_NOTPRESSED);
            fingerRight.setPosition(BUTTON_NOTPRESSED);
        }
    }

    public boolean ifPushLeftButton() {
        if (colorSensorB.blue() > 1) { // if a blue is detected
            // resets the red streak when a blue is initially detected
            if (detectBlueStreak == 0) detectRedStreak = 0;
            // u should know this...
            detectBlueStreak++;
            // if there is less than 10 red detections within the 25 blue detection streak
            // the code is sure the detection is really blue.
            if (detectBlueStreak > 50) {
                if (detectRedStreak < 20) {
                    // determines that the beaconLeft is really blue, and resets the red streak
                    detectRedStreak = 0;
                    beaconLeftIsBlue = true;
                }
                if (detectRedStreak >= 20) {
                    // resets both streaks and start over when there is too much red in the blue streak
                    detectBlueStreak = 0;
                    detectRedStreak = 0;
                }
            }
        } else {
            if (detectRedStreak == 0) detectBlueStreak = 0;
            detectRedStreak++;
            if (detectRedStreak > 50){
                if (detectBlueStreak < 20) {
                    detectBlueStreak = 0;
                    beaconLeftIsBlue = false;
                }
                if (detectBlueStreak >= 20) {
                    detectBlueStreak = 0;
                    detectRedStreak = 0;
                }
            }
        }
        return !(beaconLeftIsBlue ^ !isRed); // check if the left beacon matches the team color (NOT(XOR))
    }

    //
    // gyroTurn(howMuch)
    //
    // Start a gyro-based turn.   howMuch is the number of degrees you want to turn,
    // which can be negative for right turns.
    //
    // It returns 'true' if the turn has just started or is still in progress
    // 'false' is returned when the turn is complete.
    //

    public boolean gyroTurn(int howMuch) {

        motorFrontRight.setMode(DcMotorController.RunMode.RUN_WITHOUT_ENCODERS);
        motorFrontLeft.setMode(DcMotorController.RunMode.RUN_WITHOUT_ENCODERS);

        // We update the gyro's currentHeading each time the loop is called.
        // It runs on its own, we query the current compass position by reading the
        // latest value that checkGyro gets from the compass.
        gyroReader.checkGyro();

        if (gyroTurnIsRunning) {
            int curHeading = gyroReader.getHeading();

            degreesToTurn = subtractHeadings(destHeading, curHeading);

            //double turnSpeed = 0.10;
            //double slowTurnSpeed = 0.04;

            double turnSpeed = 0.10;
            double slowTurnSpeed = 0.10;

            // If we need to turn

            DbgLog.msg("TURN: myHeading:" + curHeading + " dest " + (destHeading) + " left:" + degreesToTurn + (shouldTurnLeft ? " LEFT" : " RIGHT"));

            if (Math.abs(degreesToTurn) <= 20) {
                turnSpeed = slowTurnSpeed;
            }

            if (Math.abs(degreesToTurn) <= 1) {
                motorFrontLeft.setPower(0);
                motorFrontRight.setPower(0);
                motorBackLeft.setPower(0);
                motorBackRight.setPower(0);
                gyroTurnIsRunning = false;

            } else if (shouldTurnLeft) {
                motorFrontLeft.setPower(-turnSpeed);
                motorFrontRight.setPower(turnSpeed);
                motorBackLeft.setPower(-turnSpeed);
                motorBackRight.setPower(turnSpeed);
            } else {
                motorFrontLeft.setPower(turnSpeed);
                motorFrontRight.setPower(-turnSpeed);
                motorBackLeft.setPower(turnSpeed);
                motorBackRight.setPower(-turnSpeed);
            }
        } else {
            destHeading = normalizeHeading(gyroReader.getHeading() + howMuch);

            DbgLog.msg("STARTING TURN: amount " + howMuch + " destHeading " + destHeading);

            shouldTurnLeft = !(degreesToTurn > 0); // if-then-else on one line
            gyroTurnIsRunning = true;
        }
        return gyroTurnIsRunning;
    }

    // ===== moveDistance =====
    // WARNING: HAPHAZARDLY SLAPPED-TOGETHER TEMPORARILY SKETCHY FIX
    // FOR RAGE QUIT-INDUCING BUGS AHEAD!!! PROCEED AT YOUR OWN RISK.
    // ( ^^ actually nope lol its fixed ^^)

    // ***RESOLVED***
    // Seems like the motorFrontXxx.getCurrentPosition() values are +- inverted, so add a negative to getCurrentPosition()
    // Update: the RIGHT motors were reversed instead of the left ones, so that has been corrected
    // The reasoning: both left and right motors are displaying that they are moving backwards
    // so this is why they have been displaying negative values for currentPosition

    // ***RESOLVED***
    // Another bug: the motors still twitch and move a bit even after the motors have done their job
    // This has been observed in the motorFrontRight on the MentorBot, not the FrontLeft
    // Reading the telemetry data, the cause for this is the setChannelMode(RunMode.*) is not changing
    // Seems like we never needed the RunMode.RUN_TO_POSITION after all
    // Since there is already code that stops the robot, we don't need to mess with RunMode or isBusy()

    // ^^^ DUDE U WOT M8 ??!! ^^^
    // Turns out part of the problem is ***NOT ADDING "break;"*** to the end of each step in the switch statement of the loop code

    public boolean moveDistance(double distanceInInches, double motorPower) {
        // If the "move distance" routine is running, test to see if we should
        // be stopped.

        // If the "move distance" routine is not yet running, set it to run.

        // The return value of this routine is 'true' if we are still busy.

        if (moveDistanceIsRunning) {
            if ((motorFrontRight.getCurrentPosition() >= motorFrontRight.getTargetPosition() && distanceInInches > 0) || (motorFrontRight.getCurrentPosition() <= (motorFrontRight.getTargetPosition()) && distanceInInches < 0)){
                motorFrontLeft.setPower(0);
                motorFrontRight.setPower(0);
                motorBackLeft.setPower(0);
                motorBackRight.setPower(0);
                DbgLog.msg("MOVE_DISTANCE: finished");
                moveDistanceIsRunning = false;
                motorFrontRight.setMode(DcMotorController.RunMode.RUN_WITHOUT_ENCODERS);
                motorFrontLeft.setMode(DcMotorController.RunMode.RUN_WITHOUT_ENCODERS);
            }
        } else {
            motorFrontRight.setMode(DcMotorController.RunMode.RUN_USING_ENCODERS);
            motorFrontLeft.setMode(DcMotorController.RunMode.RUN_USING_ENCODERS);

            int distanceInEncoderCounts = inchesToEncoder(distanceInInches);

            DbgLog.msg("Move:" + distanceInEncoderCounts + ';' + motorFrontLeft.getCurrentPosition() + ';' + motorFrontLeft.getTargetPosition());

            motorFrontLeft.setTargetPosition(motorFrontLeft.getCurrentPosition() + distanceInEncoderCounts);
            motorFrontRight.setTargetPosition(motorFrontRight.getCurrentPosition() + distanceInEncoderCounts);

            // reverse motor power if moving backward
            if (distanceInInches > 0) {
                motorFrontLeft.setPower(motorPower);
                motorFrontRight.setPower(motorPower);
                motorBackLeft.setPower(motorPower);
                motorBackRight.setPower(motorPower);
            } else if (distanceInInches < 0) {
                motorFrontLeft.setPower(-motorPower);
                motorFrontRight.setPower(-motorPower);
                motorBackLeft.setPower(-motorPower);
                motorBackRight.setPower(-motorPower);
            }
            moveDistanceIsRunning = true;
        }
        return moveDistanceIsRunning;
    }

    public boolean delay(double secs) {
        if (delayIsRunning) {
            if (startDelayTime >= stopDelayTime) {
                delayIsRunning = false;
            }
        } else {
            startDelayTime = time;
            stopDelayTime = time + secs;
            delayIsRunning = true;
        }
        return delayIsRunning;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////////////////////////
    //
    // **********
    // CHANGE ANYTHING BELOW THIS DEPENDING ON THE OBJECTIVES.
    // **********
    //

    //
    // The member variables below are for holding the current "state" of our program.
    // change enum based on steps in loop
    //
    enum autoStep {
        IDLE,
        DELAY,
        FORWARD_1,
        TURN_1,
        TURN_2,
        FORWARD_2,
        FORWARD_3,
        FOLLOW_LINE,
        STOP
    }

    autoStep currentStep = autoStep.IDLE;

    boolean isRed;
    boolean startNearMountain;
    double delayTime;
    boolean pushButton;
    FtcConfig.AutonType autonType;

    //
    // The start() method below is called when you press the triangle start button on the
    // driver station.   This routine is called only once.
    //

    public void start() {
        // Erase all the telemetry data.

        telemetry.clearData();


        // Set the current autonomous step to our first step, DELAY.
        currentStep = autoStep.DELAY;

        // Get config settings
        // the object ftcConfig's subclass variables hold the config
        isRed = ftcConfig.param.colorIsRed;
        autonType = ftcConfig.param.autonType;
        startNearMountain = ftcConfig.param.startNearMountain;
        delayTime = ftcConfig.param.delayInSec;
        pushButton = ftcConfig.param.pushButton;

        fingerLeft.setPosition(BUTTON_NOTPRESSED);
        fingerRight.setPosition(BUTTON_NOTPRESSED);
        zipServo.setPosition(ZIP_CLOSED);
        personDropper.setPosition(0.8);
    }

    //
    // This is the main loop method.  Note that it's not really a loop,
    // the loop() routine is called over and over by the Robot Controller main class
    // You are supposed to decide what to do based on what you were doing before, and
    // any inputs from the joysticks or sensors.
    //
    // Do not try to wait for things to happen in this routine, that will just make
    // the runtime angry.
    //

    //@Override
    public void loop() {

        // We update the gyro's currentHeading each time the loop is called.
        // It runs on its own, we query the current compass position by reading the
        // latest value that checkGyro gets from the compass.
        gyroReader.checkGyro();

        // Do not change to switch statement
        // Or "case ftcConfig.param.autonType.* " will be red lines

        if (autonType == FtcConfig.AutonType.GO_FOR_BEACON) {
            beacon();
        } else if (autonType == FtcConfig.AutonType.GO_FOR_MOUNTAIN) {
            mountain();
        } else if (autonType == FtcConfig.AutonType.GO_FOR_BOTH) {
            beacon();
            beaconToMountain();
        } else if (autonType == FtcConfig.AutonType.TEST) { // ftcConfig.param.autonType.TEST will yield access via static message
            test();
        }

        fingerRight.setDirection(Servo.Direction.FORWARD);
        fingerLeft.setDirection(Servo.Direction.REVERSE);

        telemetry.addData("TIME ELAPSED: ", time);
        telemetry.addData("TIME LEFT", 30 - time);
        telemetry.addData("enc: ", motorFrontLeft.getCurrentPosition() + " " + motorFrontRight.getCurrentPosition() + " " + motorFrontLeft.getTargetPosition());
        telemetry.addData("mode", motorFrontLeft.getMode() + " " + motorFrontRight.getMode());
        telemetry.addData("gyro: ", " cur " + gyroReader.curHeading + " dest " + destHeading + " degsToTurn " + degreesToTurn);
        telemetry.addData("currentStep: ", currentStep);
        telemetry.addData("autonType: ", autonType);
        telemetry.addData("finger pos: ", fingerLeft.getPosition() + " " + fingerRight.getPosition());
        telemetry.addData("personDropper pos: ", personDropper.getPosition());
    }

    // TODO the values in the parentheses still have to be changed as the robot is tested
    // put everything that should have been in the loop method (the switch statement)

    public void test() {
        switch (currentStep) {
            case DELAY:
                currentStep = autoStep.FORWARD_1;
                break;
            case FORWARD_1:
                if (!moveDistance(18, 0.3)) currentStep = autoStep.FOLLOW_LINE;
                break;
            case FOLLOW_LINE:
                if (!lineFollower()) currentStep = autoStep.STOP;
                break;
            case STOP:
                break;
        }
    }

    void beacon() {
        switch (currentStep) {
            case IDLE:
                break;
            case DELAY:
                //if (!delay(delayTime)) {
                    currentStep = autoStep.FORWARD_1;
                //}
                break;
            case FORWARD_1: //leave spawn
                if (!moveDistance(18, 0.3)) {  //12 in per sec
                    currentStep = autoStep.TURN_1;
                }
                break;
            case TURN_1:
                if (isRed) {
                    if (!gyroTurn(45)) { //spins left 45 degs
                        currentStep = autoStep.FORWARD_2;
                    }
                } else if (!isRed) {
                    if (!gyroTurn(-45)) {
                        currentStep = autoStep.FORWARD_2;
                    }
                }
                break;
            case FORWARD_2:
                if (!moveDistance(45, 0.3)) {
                    currentStep = autoStep.TURN_2;
                }
                break;
            case TURN_2: //turns towards beacon, prepare for line follow
                //depends on the initial place of the robot
                if (!startNearMountain) {
                    if (isRed) {
                        if (!gyroTurn(25)) {
                            currentStep = autoStep.FORWARD_3;
                        }
                    } else if (!isRed) {
                        if (!gyroTurn(-25)) {
                            currentStep = autoStep.FORWARD_3;
                        }
                    }
                }
                break;
            case FORWARD_3: //proceed to beacon
                if (!moveDistance(18.0, 0.3)) {
                    currentStep = autoStep.FOLLOW_LINE;
                }
                break;
            case FOLLOW_LINE: //follows line to the beacon
                if (!lineFollower()) {
                    currentStep = autoStep.STOP;
                }
                break;
            case STOP:
                break;
        }
    }

    void beaconToMountain() {
        switch (currentStep) {
            case IDLE:
                break;
            case FORWARD_1: //back up from beacon
                if (!moveDistance(-3.0, 0.3)) {
                    currentStep = autoStep.TURN_1;
                }
                break;
            case TURN_1: //turn 180 degs
                if (!gyroTurn(180)) {
                    currentStep = autoStep.FORWARD_2;
                }
                break;
            case FORWARD_2: //continue moving away from beacon
                if (!moveDistance(18.0, 0.3)) { //TODO move how much?
                    currentStep = autoStep.TURN_2;
                }
                break;
            case TURN_2: //turn towards the mountain
                if (isRed) {
                    if (!gyroTurn(135)) { //TODO may have to be changed
                        currentStep = autoStep.FORWARD_3;
                    }
                } else if (!isRed) {
                    if (!gyroTurn(-135)) {
                        currentStep = autoStep.FORWARD_3;
                    }
                }
                break;
            case FORWARD_3: //move up
                if (!moveDistance(18.0, 0.3)) {
                    currentStep = autoStep.STOP;
                }
                break;
            case STOP:
                //teleopMode();
                break;
        }
    }

    void mountain() {
        switch (currentStep) {
            case IDLE:
                break;
            case DELAY:
                //if (!delay(delayTime)) {
                    currentStep = autoStep.FORWARD_1;
                //}
                break;
            case FORWARD_1: //leave spawn
                if (!moveDistance(18.0, 0.3)) {
                    currentStep = autoStep.TURN_1;
                }
                break;
            case TURN_1:
                if (isRed) {
                    if (!gyroTurn(45)) {
                        currentStep = autoStep.FORWARD_2;
                    }
                } else if (!isRed) {
                    if (!gyroTurn(-45)) {
                        currentStep = autoStep.FORWARD_2;
                    }
                }
                break;
            case FORWARD_2:
                // TODO
                if (startNearMountain) {
                    if (!moveDistance(33.5, 0.3)) {
                        currentStep = autoStep.TURN_2;
                    }
                } else if (!startNearMountain) {
                    if (!moveDistance(50, 0.3)) {
                        currentStep = autoStep.TURN_2;
                    }
                }
                break;
            case TURN_2: //turn towards the mountain
                if (isRed) {
                    if (!gyroTurn(90)) {
                        currentStep = autoStep.FORWARD_3;
                    }
                } else if (!isRed) {
                    if (!gyroTurn(-90)) {
                        currentStep = autoStep.FORWARD_3;
                    }
                }
                break;
            case FORWARD_3: //move up the mountain
                if (startNearMountain) {
                    if (!moveDistance(36, 0.3)) {
                        currentStep = autoStep.STOP;
                    }
                } else if (!startNearMountain) {
                    if (!moveDistance(54, 0.3)) {
                        currentStep = autoStep.STOP;
                    }
                }
                break;
            case STOP:
                break;
        }
    }

    //
    // This method is called when the OpMode is stopped.
    //

    public void stop() {
        //well it means stop.
    }
}