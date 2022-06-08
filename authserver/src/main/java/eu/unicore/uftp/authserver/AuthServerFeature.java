package eu.unicore.uftp.authserver;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import eu.unicore.services.DeploymentDescriptor;
import eu.unicore.services.Kernel;
import eu.unicore.services.rest.RestService;
import eu.unicore.services.rest.security.UserPublicKeyCache;
import eu.unicore.services.rest.security.UserPublicKeyCache.UserInfoSource;
import eu.unicore.services.utils.deployment.DeploymentDescriptorImpl;
import eu.unicore.services.utils.deployment.FeatureImpl;

/**
 * job execution
 * 
 * @author schuller
 */
public class AuthServerFeature extends FeatureImpl {

	public AuthServerFeature(Kernel kernel) {
		this();
		setKernel(kernel);
	}
	
	public AuthServerFeature() {
		this.name = "AuthServer";
	}

	public void setKernel(Kernel kernel) {
		super.setKernel(kernel);
		getStartupTasks().add(new AuthServerStartup(kernel));
	}
	
	public List<DeploymentDescriptor> getServices(){
		List<DeploymentDescriptor> services = new ArrayList<>();
		services.add(new AuthSD(kernel));		
		return services;
	}
	
	
	public static class AuthSD extends DeploymentDescriptorImpl {

		public AuthSD(Kernel kernel){
			this();
			setKernel(kernel);
		}
		
		public AuthSD() {
			super();
			this.name = "auth";
			this.type = RestService.TYPE;
			this.implementationClass = AuthService.class;
		}
	}

	
	public static class AuthServerStartup implements Runnable {

		private final Kernel kernel;

		public AuthServerStartup(Kernel kernel){
			this.kernel = kernel;
		}

		public void run() {
			UserPublicKeyCache cache = kernel.getAttribute(UserPublicKeyCache.class);
			if(cache!=null) {
				Collection<UserInfoSource>sources = cache.getUserInfoSources();
				for(UserInfoSource s: sources) {
					if(s instanceof UserPubKeyLoader)return;
				}
				sources.add(new UserPubKeyLoader(kernel));
			}
		}
	}
}
