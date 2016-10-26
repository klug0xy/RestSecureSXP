package rest.impl;


import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.KeyPairGenerator;
import java.security.KeyPair;
import java.security.Security;
import java.math.BigInteger;
import java.util.Date;

import javax.security.auth.x500.X500Principal;
import java.security.cert.X509Certificate;

import org.bouncycastle.x509.extension.SubjectKeyIdentifierStructure;
import org.bouncycastle.x509.X509V3CertificateGenerator;
import org.bouncycastle.asn1.x509.X509Extensions;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.SslConnectionFactory;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.server.SecureRequestCustomizer;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.SecureRequestCustomizer;
import org.eclipse.jetty.server.SslConnectionFactory;
import org.eclipse.jetty.http.HttpVersion;

import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;

import org.eclipse.jetty.webapp.WebAppContext;


import java.io.IOException;

import com.google.common.reflect.ClassPath;

import rest.api.RestServer;
import rest.api.ServletPath;

public class JettyRestServer implements RestServer{
	
	private ServletContextHandler context;
	private Server server;
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public void initialize(String packageName) {
		context = new ServletContextHandler(ServletContextHandler.SESSIONS);
        context.setContextPath("/");
        
        final ClassLoader loader = Thread.currentThread().getContextClassLoader();

        try {
			for (final ClassPath.ClassInfo info : ClassPath.from(loader).getTopLevelClasses()) {
			  if (info.getName().startsWith(packageName + ".")) {
			    final Class<?> clazz = info.load();
			    ServletPath path = clazz.getAnnotation(ServletPath.class);
				if(path == null) {
					continue;
				}
				ServletHolder jerseyServlet = context.addServlet(org.glassfish.jersey.servlet.ServletContainer.class, path.value());
				jerseyServlet.setInitOrder(0);
				jerseyServlet.setInitParameter("jersey.config.server.provider.classnames", clazz.getCanonicalName());
			  }
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
        
        /*for(Class<?> c : entryPoints) {
        	
        	ServletPath path = c.getAnnotation(ServletPath.class);
        	if(path == null) {
        		throw new RuntimeException("No servlet path annotation on class " + c.getCanonicalName());
        	}
        	ServletHolder jerseyServlet = context.addServlet(org.glassfish.jersey.servlet.ServletContainer.class, path.value());
        	jerseyServlet.setInitOrder(0);
        	jerseyServlet.setInitParameter("jersey.config.server.provider.classnames", c.getCanonicalName());
        }*/
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void start(int port) throws Exception {
		server = new Server();
		
		Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());

		//Mot de passe.
		char[] password = {'1', '2', '3', '4', '5', '6'};
		KeyStore.PasswordProtection protected_password = new KeyStore.PasswordProtection(password);

		//Création du keystore.
		KeyStore ks = KeyStore.getInstance("jks");
		ks.load(null, password);  //Chargement à partir de rien (creation du keystore et non importation).

		//Création de la paire de clef.
		KeyPairGenerator key_gen = KeyPairGenerator.getInstance("RSA");		
		key_gen.initialize(1024);
		KeyPair keys = key_gen.genKeyPair();

		//Création du certificat.
		X509V3CertificateGenerator cert_gen = new X509V3CertificateGenerator();

		X500Principal cn = new X500Principal("CN=SXP");
		cert_gen.setSerialNumber(new BigInteger("123456789"));
		cert_gen.setIssuerDN(cn);
		cert_gen.setNotBefore(new Date(System.currentTimeMillis() - 24 * 60 * 60 * 1000));
		cert_gen.setNotAfter(new Date(System.currentTimeMillis() + 365 * 24 * 60 * 60 * 1000));
		cert_gen.setSubjectDN(cn);
		cert_gen.setPublicKey(keys.getPublic());
		cert_gen.setSignatureAlgorithm("MD5WithRSA"); //SHA256withRSA
		
		
		X509Certificate[] cert_chain = new X509Certificate[1];
		cert_chain[0] = cert_gen.generateX509Certificate(keys.getPrivate(), "BC"); //CA private key (auto signed)

		ks.setEntry("SXP",
				new KeyStore.PrivateKeyEntry(keys.getPrivate(), cert_chain),
				new KeyStore.PasswordProtection(password));


		//Enregistement du keystore dans un fichier.
		java.io.FileOutputStream fos = null;
		try 
		{
			fos = new java.io.FileOutputStream("keystore.jks");
			ks.store(fos, password);
		}
		finally 
		{
			if(fos != null)
				fos.close();
		}

		// Http config+++
		HttpConfiguration http_config = new HttpConfiguration();
		http_config.setSecureScheme("https");
		http_config.setSecurePort(port);
		http_config.setOutputBufferSize(38768);
		
		// SSL Context factory for HTTPS
		SslContextFactory sslContextFactory = new SslContextFactory();
		sslContextFactory.setKeyStorePath("./keystore.jks");
		sslContextFactory.setKeyStorePassword("123456");
		sslContextFactory.setKeyManagerPassword("123456");

		// HTTPS Config
		HttpConfiguration https_config = new HttpConfiguration(http_config);
		SecureRequestCustomizer src = new SecureRequestCustomizer();
		src.setStsMaxAge(2000);
		src.setStsIncludeSubDomains(true);
		https_config.addCustomizer(src);
		
		// HTTPS Connector
		ServerConnector https = new ServerConnector(server,
						    new SslConnectionFactory(sslContextFactory, HttpVersion.HTTP_1_1.asString()),
						    new HttpConnectionFactory(https_config));
		https.setPort(port);
		https.setIdleTimeout(500000);

		server.setConnectors(new Connector[] {https}); //http, 
	
		

			
        server.setHandler(context);
		server.start();
        server.join();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void stop() {
		server.destroy();
	}

}