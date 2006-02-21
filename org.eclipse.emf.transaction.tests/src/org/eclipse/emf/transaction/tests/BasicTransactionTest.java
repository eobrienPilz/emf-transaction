/**
 * <copyright>
 *
 * Copyright (c) 2005 IBM Corporation and others.
 * All rights reserved.   This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   IBM - Initial API and implementation
 *
 * </copyright>
 *
 * $Id: BasicTransactionTest.java,v 1.3 2006/02/21 22:16:40 cmcgee Exp $
 */
package org.eclipse.emf.transaction.tests;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import junit.framework.Test;
import junit.framework.TestSuite;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.emf.common.command.Command;
import org.eclipse.emf.common.notify.Notification;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.edit.command.SetCommand;
import org.eclipse.emf.examples.extlibrary.Book;
import org.eclipse.emf.examples.extlibrary.EXTLibraryFactory;
import org.eclipse.emf.examples.extlibrary.EXTLibraryPackage;
import org.eclipse.emf.transaction.RollbackException;
import org.eclipse.emf.transaction.RunnableWithResult;
import org.eclipse.emf.transaction.TransactionalCommandStack;
import org.eclipse.emf.transaction.Transaction;
import org.eclipse.emf.transaction.impl.InternalTransactionalEditingDomain;
import org.eclipse.emf.transaction.internal.EMFTransactionStatusCodes;
import org.eclipse.emf.transaction.tests.fixtures.TestListener;



public class BasicTransactionTest extends AbstractTest {

	public BasicTransactionTest(String name) {
		super(name);
	}
	
	public static Test suite() {
		return new TestSuite(BasicTransactionTest.class, "Basic Transaction Tests"); //$NON-NLS-1$
	}

	/**
	 * Tests that read transactions are not actually enforced for EList initialization,
	 * etc.
	 */
	public void test_read() {
		try {
			// should be able to read with running exclusive, as we cannot
			//   actually enforce the protocol
			assertNotNull(find("root/Root Book")); //$NON-NLS-1$
		} catch (Exception e) {
			fail(e);
		}
	}

	/**
	 * Tests that we can read in a read-only transaction.
	 */
	public void test_read_readOnlyTransaction() {
		// should be able to read in a read-only transaction
		startReading();
		
		assertNotNull(find("root/Root Book")); //$NON-NLS-1$
		
		commit();
	}

	/**
	 * Tests that we can read in a read-write transaction.
	 */
	public void test_read_readWriteTransaction() {
		// should be able to read in a read/write transaction
		startWriting();
		
		assertNotNull(find("root/Root Book")); //$NON-NLS-1$
		
		commit();
	}

	/**
	 * Tests that we can read in a <code>runExclusive()</code> runnable.
	 */
	public void test_read_exclusive() {
		try {
			// should be able to read exclusively
			final Book book[] = new Book[1];
			
			domain.runExclusive(new Runnable() {
				public void run() {
					book[0] = (Book) find("root/Root Book"); //$NON-NLS-1$
				}});
			
			assertNotNull(book[0]);
		} catch (InterruptedException e) {
			fail("Should not be interrupted"); //$NON-NLS-1$
		} catch (Exception e) {
			fail(e);
		}
	}
	
	/**
	 * Tests that we cannot write without a write transaction.
	 */
	public void test_write() {
		startReading();
		
		final Book book = (Book) find("root/Root Book"); //$NON-NLS-1$

		commit();
		
		try {
			// try to modify it
			book.setTitle("New Title"); //$NON-NLS-1$
			
			// should have thrown
			fail("Should have thrown IllegalStateException"); //$NON-NLS-1$
		} catch (IllegalStateException e) {
			// success
			trace("Got expected exception: " + e.getLocalizedMessage()); //$NON-NLS-1$
		} catch (Exception e) {
			fail(e);
		}
	}
	
	/**
	 * Tests that we cannot write in a read-only transaction.
	 */
	public void test_write_readOnlytransaction() {
		try {
			startReading();
			
			Book book = (Book) find("root/Root Book"); //$NON-NLS-1$
			
			// try to modify it in a read/write transaction
			book.setTitle("New Title"); //$NON-NLS-1$
			
			// should have thrown
			fail("Should have thrown IllegalStateException"); //$NON-NLS-1$
		} catch (IllegalStateException e) {
			// success
			trace("Got expected exception: " + e.getLocalizedMessage()); //$NON-NLS-1$
		} catch (Exception e) {
			fail(e);
		} finally {
			rollback();
		}
	}
	
	/**
	 * Tests that we can write in a read-write transaction.
	 */
	public void test_write_readWritetransaction() {
		try {
			startWriting();
			
			Book book = (Book) find("root/Root Book"); //$NON-NLS-1$
			
			// try to modify it in a read/write transaction
			book.setTitle("New Title"); //$NON-NLS-1$
			
			commit();
			
			assertEquals("New Title", book.getTitle()); //$NON-NLS-1$
		} catch (Exception e) {
			fail(e);
		} finally {
			if (getActiveTransaction() != null) {
				rollback();
			}
		}
	}
	
	/**
	 * Tests that we cannot write from a different thread than the thread that
	 * currently has a write transaction open.  Also tests that the thread that
	 * had the valid write transaction is aborted.
	 */
	public void test_write_wrongThread() {
		final Object monitor = new Object();
		
		Thread t = new Thread(new Runnable() {
		
			public void run() {
				Transaction xa = null;
				
				try {
					synchronized (monitor) {
						xa = ((InternalTransactionalEditingDomain) domain).startTransaction(true, null);
						
						// wake up the main thread
						monitor.notifyAll();
						
						// wait for the main thread to continue
						monitor.wait();
						
						// attempt commit.  Should roll back because of abort
						try {
							xa.commit();
							fail("Should have thrown RollbackException"); //$NON-NLS-1$
						} catch (RollbackException e) {
							// success
							trace("Got expected rollback: " + e.getLocalizedMessage()); //$NON-NLS-1$
						} finally {
							xa = null;
						}
					}
				} catch (Exception e) {
					fail(e);
				} finally {
					if (xa != null) {
						xa.rollback();
					}
				}
			}});
		
		try {
			synchronized (monitor) {
				t.start();
				
				// wait for the thread to start its transaction
				monitor.wait();
			}
			
			Book book = (Book) find("root/Root Book"); //$NON-NLS-1$
			
			// try to modify it in a read/write transaction
			book.setTitle("New Title"); //$NON-NLS-1$
			
			// should have thrown
			fail("Should have thrown IllegalStateException"); //$NON-NLS-1$
		} catch (IllegalStateException e) {
			// success
			trace("Got expected exception: " + e.getLocalizedMessage()); //$NON-NLS-1$
		} catch (Exception e) {
			fail(e);
		} finally {
			// let the other thread exit
			synchronized (monitor) {
				monitor.notifyAll();
			}
		}
	}
	
	/**
	 * Tests that we can use the command stack to execute a writing command.
	 */
	public void test_write_command() {
		startReading();
		
		final Book book = (Book) find("root/Root Book"); //$NON-NLS-1$

		commit();
		
		try {
			// try to modify it using a command
			
			Command cmd = new SetCommand(
				domain,
				book,
				EXTLibraryPackage.eINSTANCE.getBook_Title(),
				"New Title"); //$NON-NLS-1$
			((TransactionalCommandStack) domain.getCommandStack()).execute(cmd, null);
			
			startReading();
			
			assertEquals("New Title", book.getTitle()); //$NON-NLS-1$
			
			commit();
		} catch (Exception e) {
			fail(e);
		}
	}

	/**
	 * Tests that we can load and unload resources (having contents) without a write
	 * transaction.
	 */
	public void test_loadUnloadDuringRead() throws Exception {
		// create a new domain that hasn't yet loaded the test resource
		doTearDown();
		ResourceSet rset = new ResourceSetImpl();
		domain = createEditingDomain(rset);
		
		TestListener listener = new TestListener();
		domain.addResourceSetListener(listener);
		
		startReading();
		
		Resource res = rset.createResource(
				URI.createURI(EmfTransactionTestsBundle.getEntry(
					"/test_models/test_model.extlibrary").toString())); //$NON-NLS-1$

		res.load(Collections.EMPTY_MAP);
		
		commit();
		
		// check that we got the expected events
		assertNotNull(listener.postcommit);
		List notifications = listener.postcommitNotifications;
		assertFalse(notifications.isEmpty());
		
		// look for an event indicating resource was loaded and one indicating
		// that a root was added.  The root added event should come first!
		Notification rootAdded = null;
		Notification resLoaded = null;
		
		for (Iterator iter = notifications.iterator(); iter.hasNext();) {
			Notification next = (Notification) iter.next();
			
			if (next.getNotifier() == res) {
				if (next.getFeatureID(null) == Resource.RESOURCE__IS_LOADED) {
					if (next.getNewBooleanValue()) {
						assertNotNull(rootAdded); // should get rootAdded first
						resLoaded = next;
					}
				} else if (next.getFeatureID(null) == Resource.RESOURCE__CONTENTS) {
					if (next.getEventType() == Notification.ADD) {
						rootAdded = next;
					}
				}
			}
		}
		
		assertNotNull(rootAdded);
		assertNotNull(resLoaded);
		
		listener.reset();  // clear stored events
		
		startReading();
		
		res.unload();
		
		commit();
		
		// check that we got the expected events
		assertNotNull(listener.postcommit);
		notifications = listener.postcommitNotifications;
		assertFalse(notifications.isEmpty());
		
		// look for an event indicating resource was unloaded and one indicating
		// that a root was removed.  The root removed event should come first!
		Notification rootRemoved = null;
		Notification resUnloaded = null;
		
		for (Iterator iter = notifications.iterator(); iter.hasNext();) {
			Notification next = (Notification) iter.next();
			
			if (next.getNotifier() == res) {
				if (next.getFeatureID(null) == Resource.RESOURCE__IS_LOADED) {
					if (!next.getNewBooleanValue()) {
						assertNotNull(rootRemoved); // should get rootRemoved first
						resUnloaded = next;
					}
				} else if (next.getFeatureID(null) == Resource.RESOURCE__CONTENTS) {
					if (next.getEventType() == Notification.REMOVE) {
						rootRemoved = next;
					}
				}
			}
		}
		
		assertNotNull(rootRemoved);
		assertNotNull(resUnloaded);
	}
	
	/**
	 * Tests that changes to the contents of a loaded resource may not be performed
	 * in a read transaction.
	 */
	public void test_resourceContentsChanges_read() {
		try {
			startReading();
			
			testResource.getContents().add(EXTLibraryFactory.eINSTANCE.createLibrary());
			
			// should have thrown
			fail("Should have thrown IllegalStateException"); //$NON-NLS-1$
		} catch (IllegalStateException e) {
			// success
			trace("Got expected exception: " + e.getLocalizedMessage()); //$NON-NLS-1$
		} catch (Exception e) {
			fail(e);
		} finally {
			rollback();
		}
	}
	
	/**
	 * Tests that changes to the contents of a loaded resource may be performed
	 * in a write transaction.
	 */
	public void test_resourceContentsChanges_write() {
		try {
			startWriting();
			
			testResource.getContents().add(EXTLibraryFactory.eINSTANCE.createLibrary());
		} catch (Exception e) {
			fail(e);
		} finally {
			rollback();
		}
	}
	
	/**
	 * Tests that we cannot add a root to a newly created resource in a read transaction.
	 */
	public void test_newResourceContentsChanges_read() {
		try {
			startReading();
			
			Resource res = domain.getResourceSet().createResource(
					URI.createFileURI("/tmp/foo.extlibrary")); //$NON-NLS-1$
			assertFalse(res.isLoaded());
			
			res.getContents().add(EXTLibraryFactory.eINSTANCE.createLibrary());
			
			// should have thrown
			fail("Should have thrown IllegalStateException"); //$NON-NLS-1$
		} catch (IllegalStateException e) {
			// success
			trace("Got expected exception: " + e.getLocalizedMessage()); //$NON-NLS-1$
		} catch (Exception e) {
			fail(e);
		} finally {
			rollback();
		}
	}
	
	/**
	 * Tests that we can add a root to a newly created resource in a write transaction.
	 */
	public void test_newResourceContentsChanges_write() {
		try {
			startWriting();
			
			Resource res = domain.getResourceSet().createResource(
					URI.createFileURI("/tmp/foo.extlibrary")); //$NON-NLS-1$
			assertFalse(res.isLoaded());
			
			res.getContents().add(EXTLibraryFactory.eINSTANCE.createLibrary());
		} catch (Exception e) {
			fail(e);
		} finally {
			rollback();
		}
	}
	
	/**
	 * Tests that a RunnableWithResult has its status set correctly when it is
	 * rolled back due to concurrent write.
	 */
	public void test_concurrentWrite_runnable() {
		final Object monitor = new Object();
		
		final Thread t = new Thread(new Runnable() {
			public void run() {
				synchronized (monitor) {
					try {
						// concurrent write
						testResource.getContents().add(EXTLibraryFactory.eINSTANCE.createLibrary());
					} catch (IllegalStateException e) {
						trace("Got expected exception: " + e.getLocalizedMessage()); //$NON-NLS-1$
					} finally {
						monitor.notify();
					}
				}
			}});
		
		try {
			RunnableWithResult rwr = new RunnableWithResult.Impl() {
				public void run() {
					synchronized (monitor) {
						t.start();

						try {
							monitor.wait();  // wait for the concurrent write
						} catch (InterruptedException e) {
							fail(e);
						}
					}
				}};
			
			domain.runExclusive(rwr);
			
			// shouldn't throw, but should get an error status
			assertNotNull(rwr.getStatus());
			assertEquals(IStatus.ERROR, rwr.getStatus().getSeverity());
			assertEquals(EMFTransactionStatusCodes.CONCURRENT_WRITE, rwr.getStatus().getCode());
		} catch (Exception e) {
			fail(e);
		}
	}

	/**
	 * Tests that we cannot close a transaction that is already closed.
	 */
	public void test_closedTransaction_close() {
		// should be able to read in a read-only transaction
		startReading();
		
		Transaction tx = commit();
		
		try {
			tx.commit();
			
			// should have thrown
			fail("Should have thrown IllegalStateException"); //$NON-NLS-1$
		} catch (IllegalStateException e) {
			// success
			trace("Got expected exception: " + e.getLocalizedMessage()); //$NON-NLS-1$
		} catch (RollbackException e) {
			fail(e);
		}
		
		try {
			tx.rollback();
			
			// should have thrown
			fail("Should have thrown IllegalStateException"); //$NON-NLS-1$
		} catch (IllegalStateException e) {
			// success
			trace("Got expected exception: " + e.getLocalizedMessage()); //$NON-NLS-1$
		}
	}
}
