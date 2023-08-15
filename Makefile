.PHONY: build bootRun bootRunDebug lint format

# Any flags to pass into the build command
GRADLEFLAGS =
ifeq ($(ENV), ci)
	GRADLEFLAGS = -PenvironmentName=ci
endif

# The build command to run
GRADLE = ./gradlew

# Note the '$@' passes the target (ex: 'build') to the command
build:
	$(GRADLE) build $(GRADLEFLAGS)

bootRun:
	$(GRADLE) bootRun --args='--spring.profiles.active=local' $(GRADLEFLAGS)

bootRunDebug:
	$(GRADLE) bootRun --args='--spring.profiles.active=local' --debug-jvm $(GRADLEFLAGS)

lint:
	$(GRADLE) ktlintCheck

format:
	$(GRADLE) ktlintFormat
