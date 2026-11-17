package fr.ledcom.signaire.core;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * The core services; applications import this configuration. Packages are named rather than referenced through
 * classes, to keep this package free of dependencies on its sub-packages.
 */
@Configuration(proxyBeanMethods = false)
@ComponentScan(basePackages = {"fr.ledcom.signaire.core.search", "fr.ledcom.signaire.core.bundles"})
public class SignaireCoreConfiguration {
}
