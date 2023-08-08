.PHONY: build bootRun

# Any flags to pass into the build command
GRADLEFLAGS =
ifeq ($(ENV), ci)
	GRADLEFLAGS = -PenvironmentName=ci
endif

# The build command to run
GRADLE = ./gradlew

# Note the '$@' passes the target (ex: 'build') to the command
build:
	$(GRADLE) $@ $(GRADLEFLAGS)

bootRun:
	$(GRADLE) $@ --args='--spring.profiles.active=local' $(GRADLEFLAGS)
