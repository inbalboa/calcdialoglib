lint:
	@printf "==> linting...\n"
	@sh gradlew lint

pub:
	@printf "==> publishing...\n"
	@sh gradlew tag release

run: lint pub
	@printf "\nPublished at %s\n\n" "`date`"

.DEFAULT_GOAL := run
.PHONY: lint pub run

