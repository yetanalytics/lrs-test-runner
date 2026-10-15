.phony: ci

ci:
	clojure -Xtest

.PHONY: pom.xml

# Generate the base library's Maven manifest for GitHub dependency submission.
pom.xml:
	clojure -X:deps mvn-pom :user nil
