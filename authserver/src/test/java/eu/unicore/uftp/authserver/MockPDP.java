/*
 * Copyright (c) 2010 ICM Uniwersytet Warszawski All rights reserved.
 * See LICENCE file for licencing information.
 *
 * Created on 2010-11-14
 * Author: K. Benedyczak <golbi@mat.umk.pl>
 */
package eu.unicore.uftp.authserver;

import eu.unicore.security.Client;
import eu.unicore.services.security.pdp.ActionDescriptor;
import eu.unicore.services.security.pdp.PDPResult;
import eu.unicore.services.security.pdp.PDPResult.Decision;
import eu.unicore.services.security.pdp.UnicoreXPDP;
import eu.unicore.services.security.util.ResourceDescriptor;

public class MockPDP implements UnicoreXPDP
{
	public MockPDP() {}

	@Override
	public PDPResult checkAuthorisation(Client c, ActionDescriptor action,
			ResourceDescriptor d) throws Exception
	{
		return new PDPResult(Decision.PERMIT, "");
	}
	
}
