/*
 * Copyright (C) 2007 TopCoder Inc., All Rights Reserved.
 */

package com.cronos.onlinereview.services.uploads;


/**
 * <p>
 * Defines the contract for managing different type of uploads and set status of submissions.
 * </p>
 *
 * <p>
 * Thread safety: the implementations must be thread safe.
 * </p>
 *
 * @author fabrizyo, cyberjag
 * @version 1.0
 */
public interface UploadServices {
    /**
     * <p>
     * Adds a new submission for an user in a particular project.
     * </p>
     *
     * <p>
     * If the project allows multiple submissions for users, it will add the new submission and return. If multiple
     * submission are not allowed for the project firstly, it will add the new submission, secondly mark previous
     * submissions as deleted and then return.
     * </p>
     *
     * @param projectId
     *            the project's id
     * @param userId
     *            the user's id
     * @param filename
     *            the file name to use
     * @return the id of the new submission
     * @throws InvalidProjectException
     *             if the project does not exist
     * @throws InvalidProjectPhaseException
     *             if neither Submission or Screening phase are opened
     * @throws InvalidUserException
     *             if the user does not exist or has not the submitter role
     * @throws PersistenceException
     *             if some error occurs in persistence layer
     * @throws UploadServicesException
     *             if some other exception occurs in the process (wrap it)
     * @throws IllegalArgumentException
     *             if any id is &lt; 0, if filename is <code>null</code> or trim to empty
     */
    public long uploadSubmission(long projectId, long userId, String filename) throws UploadServicesException;

    /**
     * <p>
     * Adds a new final fix upload for an user in a particular project. This submission always overwrite the
     * previous ones.
     * </p>
     *
     * @param projectId
     *            the project's id
     * @param userId
     *            the user's id
     * @param filename
     *            the file name to use
     * @return the id of the created final fix submission.
     * @throws InvalidProjectException
     *             if the project does not exist
     * @throws InvalidProjectPhaseException
     *             if Final Fix phase is not opened
     * @throws InvalidUserException
     *             if the user does not exist or she/he is not winner submitter
     * @throws PersistenceException
     *             if some error occurs in persistence layer
     * @throws UploadServicesException
     *             if some other exception occurs in the process (wrap it)
     * @throws IllegalArgumentException
     *             if any id is &lt; 0, if filename is <code>null</code> or trim to empty
     */
    public long uploadFinalFix(long projectId, long userId, String filename) throws UploadServicesException;

    /**
     * <p>
     * Adds a new test case upload for an user in a particular project. This submission always overwrite the
     * previous ones.
     * </p>
     *
     * @param projectId
     *            the project's id
     * @param userId
     *            the user's id
     * @param filename
     *            the file name to use
     * @return the id of the created test cases submission
     * @throws InvalidProjectException
     *             if the project does not exist
     * @throws InvalidProjectPhaseException
     *             if Review phase is not opened
     * @throws InvalidUserException
     *             if the user does not exist or has not the reviewer role
     * @throws PersistenceException
     *             if some error occurs in persistence layer
     * @throws UploadServicesException
     *             if some other exception occurs in the process (wrap it)
     * @throws IllegalArgumentException
     *             if any id is &lt; 0, if filename is <code>null</code> or trim to empty
     */
    public long uploadTestCases(long projectId, long userId, String filename) throws UploadServicesException;

    /**
     * <p>
     * Sets the status of a existing submission.
     * </p>
     *
     * @param submissionId
     *            the submission's id
     * @param submissionStatusId
     *            the submission status id
     * @param operator
     *            the operator which execute the operation
     * @throws InvalidSubmissionException
     *             if the submission does not exist
     * @throws InvalidSubmissionStatusException
     *             if the submission status does not exist
     * @throws PersistenceException
     *             if some error occurs in persistence layer
     * @throws IllegalArgumentException
     *             if any id is &lt; 0 or if operator is null or trim to empty
     */
    public void setSubmissionStatus(long submissionId, long submissionStatusId, String operator)
        throws InvalidSubmissionException, InvalidSubmissionStatusException, PersistenceException;
}
