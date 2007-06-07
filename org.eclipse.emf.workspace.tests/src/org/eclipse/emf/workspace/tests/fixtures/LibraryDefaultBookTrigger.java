/**
 * <copyright>
 *
 * Copyright (c) 2005, 2007 IBM Corporation and others.
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
 * $Id: LibraryDefaultBookTrigger.java,v 1.4 2007/06/07 14:26:02 cdamus Exp $
 */
package org.eclipse.emf.workspace.tests.fixtures;

import org.eclipse.emf.common.command.Command;
import org.eclipse.emf.common.notify.Notification;
import org.eclipse.emf.edit.command.AddCommand;
import org.eclipse.emf.examples.extlibrary.Book;
import org.eclipse.emf.examples.extlibrary.EXTLibraryFactory;
import org.eclipse.emf.examples.extlibrary.EXTLibraryPackage;
import org.eclipse.emf.examples.extlibrary.Library;
import org.eclipse.emf.transaction.NotificationFilter;
import org.eclipse.emf.transaction.TransactionalEditingDomain;
import org.eclipse.emf.transaction.TriggerListener;

/**
 * A trigger listener that sets a default name on new libraries.
 *
 * @author Christian W. Damus (cdamus)
 */
public class LibraryDefaultBookTrigger extends TriggerListener {
	private final boolean isAggregate;
	
	public LibraryDefaultBookTrigger() {
		this(false);
	}
	
	public LibraryDefaultBookTrigger(boolean isAggregate) {
		super(NotificationFilter.createFeatureFilter(
					EXTLibraryPackage.eINSTANCE.getLibrary_Branches()).and(
							NotificationFilter.createEventTypeFilter(
									Notification.ADD)));
		
		this.isAggregate = isAggregate;
	}
	
	protected Command trigger(TransactionalEditingDomain domain, Notification notification) {
		Command result = null;
		
		Library newLibrary = (Library) notification.getNewValue();
		if ((newLibrary.getBooks().isEmpty())) {
			Book newBook = EXTLibraryFactory.eINSTANCE.createBook();
			newBook.setTitle("New Book"); //$NON-NLS-1$
			result = new AddCommand(
					domain,
					newLibrary.getBooks(),
					newBook);
		}
		
		return result;
	}
	
	public boolean isAggregatePrecommitListener() {
		return isAggregate;
	}
}
