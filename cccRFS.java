package com.example.ccc;

import com.example.ccc.namespaces.*;
import java.util.Properties;
import java.util.List;
import com.tailf.conf.*;
import com.tailf.navu.*;
import com.tailf.ncs.ns.Ncs;
import com.tailf.dp.*;
import com.tailf.dp.annotations.*;
import com.tailf.dp.proto.*;
import com.tailf.dp.services.*;
import com.tailf.ncs.template.Template;
import com.tailf.ncs.template.TemplateVariables;

public class cccRFS {


    /**
     * Create callback method.
     * This method is called when a service instance committed due to a create
     * or update event.
     *
     * This method returns a opaque as a Properties object that can be null.
     * If not null it is stored persistently by Ncs.
     * This object is then delivered as argument to new calls of the create
     * method for this service (fastmap algorithm).
     * This way the user can store and later modify persistent data outside
     * the service model that might be needed.
     *
     * @param context - The current ServiceContext object
     * @param service - The NavuNode references the service node.
     * @param ncsRoot - This NavuNode references the ncs root.
     * @param opaque  - Parameter contains a Properties object.
     *                  This object may be used to transfer
     *                  additional information between consecutive
     *                  calls to the create callback.  It is always
     *                  null in the first call. I.e. when the service
     *                  is first created.
     * @return Properties the returning opaque instance
     * @throws ConfException
     */

    @ServiceCallback(servicePoint="ccc-servicepoint",
        callType=ServiceCBType.CREATE)
    public Properties create(ServiceContext context,
                             NavuNode service,
                             NavuNode ncsRoot,
                             Properties opaque)
                             throws ConfException {

        Template myTemplate = new Template(context, "ccc");
        TemplateVariables myVars = new TemplateVariables();

        try {
            // check if it is reasonable to assume that devices
            // initially has been sync-from:ed
            NavuList managedDevices = ncsRoot.
                container("devices").list("device");
            for (NavuContainer device : managedDevices) {
                if (device.list("capability").isEmpty()) {
                    String mess = "Device %1$s has no known capabilities, " +
                                   "has sync-from been performed?";
                    String key = device.getKey().elementAt(0).toString();
                    throw new DpCallbackException(String.format(mess, key));
                }
            }

            // Get the devices and interfaces list from our ccc service
            // +--rw device-if* [device]
            //    +--rw device       -> /ncs:devices/device/name
            //    +--rw interface?   -> /ncs:devices/device[ncs:name=current()/../device]/config/ios:interface/FastEthernet/name
            //
            // First build interfaces list from service.list.
            NavuList interfaces = service.list("device-if");

            // our yang model has two elements in de device-if list.
            // now get all elements from NavuList and convert to array of NavuContainers
            NavuContainer[] links = interfaces.elements().toArray(new NavuContainer[0]);
            // links is an array, show length
            System.out.println("elements: " + links.length);

            // now loop through this list.
            // we're looking for the remote loopback
            for (int i = 0; i < 2; i++) {
                // get NavuContainer of other end of the link
                NavuContainer devices = links[1-i];

                // get nodename from value leaf devices
                String nodename = devices.leaf("device").valueAsString();
                System.out.println("remote nodename: " + nodename);

                // we can also get value like this:
                String currentnode = links[i].leaf("device").valueAsString();
                System.out.println("current nodename: " + currentnode);

                // now let's lookup loopback address using xpath
                // xPathSelect will deliver a List of NavuNodes with only 1 result.
                List<NavuNode> loopback = ncsRoot.container("devices").list("device").elem(nodename).container("config").xPathSelect("junos:configuration/interfaces/interface[name='lo0']/unit[name='0']/family/inet/address");

                // now use get() to get our only element from list, it should be a list of leafs.
                NavuListEntry addressNode = (NavuListEntry)loopback.get(0);
  
                // now get value from leaf, the looback address of remote node
                String address = addressNode.leaf("name").valueAsString().split("/")[0];
                System.out.println("Remote Loopback address: " + address);

                // set some unique var's to be used in XML template
                String XMLloopback = "LOOPBACK" + i;
                String XMLnode = "NODE" + i;
                String XMLinterface = "INTERFACE" + i;

                String interf = links[i].leaf("interface").valueAsString();
                System.out.println("Interface: " + interf);

                myVars.putQuoted(XMLloopback, address);
                myVars.putQuoted(XMLnode, currentnode);
                myVars.putQuoted(XMLinterface, interf);
                System.out.println(myVars);
            }
        } catch (DpCallbackException e) {
            throw (DpCallbackException) e;
        } catch (Exception e) {
            throw new DpCallbackException("Not able to check devices", e);
        }

        try {
            // apply the template with the variable
            myVars.putQuoted("ID", service.leaf("name").valueAsString());
            System.out.println(myVars);
            myTemplate.apply(service, myVars);

        } catch (Exception e) {
            throw new DpCallbackException(e.getMessage(), e);
        }
        return opaque;
    }


    /**
     * Init method for selftest action
     */
    @ActionCallback(callPoint="ccc-self-test", callType=ActionCBType.INIT)
    public void init(DpActionTrans trans) throws DpCallbackException {
    }

    /**
     * Selftest action implementation for service
     */
    @ActionCallback(callPoint="ccc-self-test", callType=ActionCBType.ACTION)
    public ConfXMLParam[] selftest(DpActionTrans trans, ConfTag name,
                                   ConfObject[] kp, ConfXMLParam[] params)
    throws DpCallbackException {
        try {
            // Refer to the service yang model prefix
            String nsPrefix = "ccc";
            // Get the service instance key
            String str = ((ConfKey)kp[0]).toString();

          return new ConfXMLParam[] {
              new ConfXMLParamValue(nsPrefix, "success", new ConfBool(true)),
              new ConfXMLParamValue(nsPrefix, "message", new ConfBuf(str))};

        } catch (Exception e) {
            throw new DpCallbackException("self-test failed", e);
        }
    }
}
