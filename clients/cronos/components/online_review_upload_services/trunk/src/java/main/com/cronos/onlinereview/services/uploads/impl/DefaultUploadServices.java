/*
 * Copyright (C) 2007 TopCoder Inc., All Rights Reserved.
 */

package com.cronos.onlinereview.services.uploads.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.cronos.onlinereview.autoscreening.management.ScreeningTaskAlreadyExistsException;
import com.cronos.onlinereview.services.uploads.ConfigurationException;
import com.cronos.onlinereview.services.uploads.InvalidProjectException;
import com.cronos.onlinereview.services.uploads.InvalidProjectPhaseException;
import com.cronos.onlinereview.services.uploads.InvalidSubmissionException;
import com.cronos.onlinereview.services.uploads.InvalidSubmissionStatusException;
import com.cronos.onlinereview.services.uploads.InvalidUserException;
import com.cronos.onlinereview.services.uploads.ManagersProvider;
import com.cronos.onlinereview.services.uploads.PersistenceException;
import com.cronos.onlinereview.services.uploads.UploadServices;
import com.cronos.onlinereview.services.uploads.UploadServicesException;
import com.topcoder.management.deliverable.Submission;
import com.topcoder.management.deliverable.SubmissionStatus;
import com.topcoder.management.deliverable.Upload;
import com.topcoder.management.deliverable.UploadStatus;
import com.topcoder.management.deliverable.UploadType;
import com.topcoder.management.deliverable.persistence.UploadPersistenceException;
import com.topcoder.management.deliverable.search.SubmissionFilterBuilder;
import com.topcoder.management.phase.PhaseManagementException;
import com.topcoder.management.project.Project;
import com.topcoder.management.resource.Resource;
import com.topcoder.management.resource.ResourceManager;
import com.topcoder.management.resource.ResourceRole;
import com.topcoder.management.resource.persistence.ResourcePersistenceException;
import com.topcoder.management.resource.search.ResourceFilterBuilder;
import com.topcoder.project.phases.Phase;
import com.topcoder.project.phases.PhaseStatus;
import com.topcoder.search.builder.SearchBuilderException;
import com.topcoder.search.builder.filter.AndFilter;
import com.topcoder.search.builder.filter.Filter;
import com.topcoder.search.builder.filter.OrFilter;
import com.topcoder.util.log.Level;
import com.topcoder.util.log.Log;
import com.topcoder.util.log.LogManager;

/**
 * <p>
 * This is the default implementation of <code>UploadServices</code> interface. It manages different type of
 * upload. It used all managers from <code>ManagerProvider</code> to perform several operations. All the methods
 * are logged. It's possible to construct the instance through configuration and <code>ObjectFactory</code> and
 * set via constructor.
 * </p>
 * <p>
 * A sample configuration file that can be used is given below.
 *
 * <pre>
 *  &lt;Config name=&quot;com.cronos.onlinereview.services.uploads.impl.DefaultUploadServices&quot;&gt;
 *      &lt;Property name=&quot;objectFactoryNamespace&quot;&gt;
 *          &lt;Value&gt;myObjectFactory&lt;/Value&gt;
 *      &lt;/Property&gt;
 *      &lt;Property name=&quot;managersProviderIdentifier&quot;&gt;
 *          &lt;Value&gt;managersProvider&lt;/Value&gt;
 *      &lt;/Property&gt;
 *  &lt;/Config&gt;
 * </pre>
 *
 * </p>
 * <p>
 * Thread safety: the thread safety is completely relied to the managers implementations because it's impossible to
 * change the other variables.
 * </p>
 *
 * @author fabrizyo, cyberjag
 * @version 1.0
 */
public class DefaultUploadServices implements UploadServices {

    /**
     * <p>
     * Represents the default namespace for this class used to load the configuration with
     * <code>ConfigManager</code>.
     * </p>
     */
    public static final String DEFAULT_NAMESPACE = DefaultUploadServices.class.getName();

    /**
     * <p>
     * Represents the logger to log all operations, exceptions, etc. It is initialized statically.
     * </p>
     */
    private static final Log LOG = LogManager.getLog(DefaultUploadServices.class.getName());

    /**
     * <p>
     * It contains all the managers used in this class. When you meet a Manager you must use the related getter
     * methods of this <code>ManagersProvider</code>. It is defined in constructor and cannot be
     * <code>null</code>.
     * </p>
     */
    private final ManagersProvider managersProvider;

    /**
     * <p>
     * Creates <code>DefaultUploadServices</code> with the specified managersProvider.
     * </p>
     *
     * @param managersProvider
     *            the provider of managers used by this class
     * @throws IllegalArgumentException
     *             if managersProvider argument is <code>null</code>
     */
    public DefaultUploadServices(ManagersProvider managersProvider) {
        Helper.checkNull(managersProvider, "managersProvider", LOG);
        this.managersProvider = managersProvider;
    }

    /**
     * <p>
     * Creates <code>DefaultUploadServices</code> using the configuration with default namespace.
     * </p>
     *
     * @throws ConfigurationException
     *             If any error occurs during accessing configuration. If bad configuration is detected.
     */
    public DefaultUploadServices() throws ConfigurationException {
        this(DEFAULT_NAMESPACE);
    }

    /**
     * <p>
     * Creates <code>DefaultUploadServices</code> using the configuration with specified namespace.
     * </p>
     *
     * @param namespace
     *            the namespace to load configuration
     * @throws ConfigurationException
     *             If any error occurs during accessing configuration. If bad configuration is detected.
     * @throws IllegalArgumentException
     *             if namespace is <code>null</code> or trim to empty
     */
    public DefaultUploadServices(String namespace) throws ConfigurationException {
        Helper.checkString(namespace, "namespace", LOG);
        this.managersProvider = (ManagersProvider) Helper.createObject(namespace, "managersProviderIdentifier",
                "DefaultManagerProvider", LOG, ManagersProvider.class, new DefaultManagersProvider());
        LOG.log(Level.INFO, "ManagersProvider created using ObjectFactory");
    }

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
     * @throws PersistenceException
     *             if some error occurs in persistence layer
     * @throws UploadServicesException
     *             if some other exception occurs in the process (wrap it)
     * @throws InvalidProjectException
     *             if the project does not exist
     * @throws InvalidProjectPhaseException
     *             if neither Submission or Screening phase are opened
     * @throws InvalidUserException
     *             if the user does not exist or has not the submitter role
     * @throws IllegalArgumentException
     *             if any id is &lt; 0, if filename is <code>null</code> or trim to empty
     */
    public long uploadSubmission(long projectId, long userId, String filename) throws UploadServicesException {
        LOG.log(Level.DEBUG, "Entered DefaultUploadServices#uploadSubmission(long, long, String)");
        Helper.checkId(projectId, "projectId", LOG);
        Helper.checkId(userId, "userId", LOG);
        Helper.checkString(filename, "filename", LOG);

        // check if the project exists
        Project project = getProject(projectId);

        // check that the user exists and has the submitter role
        Resource resource = getResource(projectId, userId, new String[] {"Submitter" });

        try {
            com.topcoder.project.phases.Project projectPhases = managersProvider.getPhaseManager().getPhases(
                    projectId);
            Phase[] phases = projectPhases.getAllPhases();
            // iterate over the phases to find if the type is "Submission" or "Screening"
            for (Phase phase : phases) {
                if (phase.getPhaseType() != null
                        && ("Submission".equals(phase.getPhaseType().getName()) || "Screening".equals(phase
                                .getPhaseType().getName()))) {
                    // check if submission or screening phase are open checking its the status
                    if (PhaseStatus.OPEN.equals(phase.getPhaseStatus())) {
                        // create a new Submission
                        Submission submission = new Submission();

                        // iterate over all SubmissionStatuses, get the SubmissionStatus with name "Active"
                        // and set to submission
                        SubmissionStatus[] submissionStatus = managersProvider.getUploadManager()
                                .getAllSubmissionStatuses();
                        for (SubmissionStatus status : submissionStatus) {
                            if ("Active".equals(status.getName())) {
                                submission.setSubmissionStatus(status);
                                break;
                            }
                        }

                        Upload upload = createUpload(projectId, userId, filename, "Submission");

                        String operator = "" + userId;
                        // persist the upload
                        managersProvider.getUploadManager().createUpload(upload, operator);

                        LOG.log(Level.INFO,
                                "Created submission Upload for project {0}, user {1} with file name {2}.",
                                new Object[] {projectId, userId, filename });

                        // persist the submission with uploadManager.createSubmission with the useId as
                        // operator
                        managersProvider.getUploadManager().createSubmission(submission, operator);

                        LOG.log(Level.INFO, "Created submission for project {0}, user {1}.", new Object[] {
                            projectId, userId });

                        // associate the submission with the submitter resource
                        resource.addSubmission(submission.getId());

                        LOG.log(Level.INFO, "Added submission {0} to resource.",
                                new Object[] {submission.getId() });

                        // Persist the resource using ResourceManager#updateResource
                        managersProvider.getResourceManager().updateResource(resource, operator);

                        LOG.log(Level.INFO, "Updated resource using the operator {0}.", new Object[] {operator });

                        // initiate the screening
                        managersProvider.getScreeningManager().initiateScreening(submission.getId(), operator);
                        LOG.log(Level.INFO, "Initiated screening for submission {0} using operator {1}.",
                                new Object[] {submission.getId(), operator });

                        // If the project DOESN'T allow multiple submissions hence its property "Allow
                        // multiple submissions" will be false
                        Boolean allow = Boolean.parseBoolean((String) project
                                .getProperty("Allow multiple submissions"));

                        if (!allow) {
                            deletePreviousSubmissions(userId, resource, submissionStatus);
                            LOG.log(Level.INFO, "Marked previous submissions for deletion for the user {0}.",
                                    new Object[] {userId });
                        }

                        return submission.getId();

                    } else { // end of if PhaseStatus.OPEN
                        LOG.log(Level.ERROR, "The 'Submission or Screening' phase is not OPEN for phaseId {0}",
                                new Object[] {phase.getId() });
                        throw new InvalidProjectPhaseException("The 'Submission or Screening' phase is not OPEN",
                                phase.getId());
                    }

                } // end of Submission or Screening

            } // end of for loop
            LOG.log(Level.ERROR, "Failed to upload submission for the projectId {0}, userId {1}", new Object[] {
                    project.getId(), userId });
            throw new InvalidProjectException("Failed to upload submission for the project", project.getId());
        } catch (PhaseManagementException e) {
            LOG.log(Level.ERROR, e, "Failed to upload submission for user {0} and project {1}.", new Object[] {
                userId, projectId });
            throw new UploadServicesException("Failed to upload submission for user " + userId + " and project "
                    + projectId + ".", e);
        } catch (UploadPersistenceException e) {
            LOG.log(Level.ERROR, e, "Failed to upload submission for user {0} and project {1}.", new Object[] {
                userId, projectId });
            throw new PersistenceException("Failed to upload submission for user " + userId + " and project "
                    + projectId + ".", e);
        } catch (com.cronos.onlinereview.autoscreening.management.PersistenceException e) {
            LOG.log(Level.ERROR, e, "Failed to upload submission for user {0} and project {1}.", new Object[] {
                userId, projectId });
            throw new PersistenceException("Failed to upload submission for user " + userId + " and project "
                    + projectId + ".", e);
        } catch (ScreeningTaskAlreadyExistsException e) {
            LOG.log(Level.ERROR, e, "Failed to upload submission for user {0} and project {1}.", new Object[] {
                userId, projectId });
            throw new UploadServicesException("Failed to upload submission for user " + userId + " and project "
                    + projectId + ".", e);
        } catch (ResourcePersistenceException e) {
            LOG.log(Level.ERROR, e, "Failed to update resource submission for user {0} and project {1}.", new Object[] {
                userId, projectId });
            throw new PersistenceException("Failed to update resource for user " + userId + " and project "
                        + projectId + ".", e);
        } finally {
            LOG.log(Level.DEBUG, "Exited DefaultUploadServices#uploadSubmission(long, long, String)");
        }
    }

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
    public long uploadFinalFix(long projectId, long userId, String filename) throws UploadServicesException {
        LOG.log(Level.DEBUG, "Entered DefaultUploadServices#uploadFinalFix(long, long, String)");
        Helper.checkId(projectId, "projectId", LOG);
        Helper.checkId(userId, "userId", LOG);
        Helper.checkString(filename, "filename", LOG);

        // check if the project exists
        Project project = getProject(projectId);

        // check that the user exists and has the submitter role
        Resource resource = getResource(projectId, userId, new String[] {"Submitter" });

        // Check that the user is the winner
        Long winnerId = (Long) project.getProperty("Winner External Reference ID");
        if (winnerId != userId) {
            throw new InvalidUserException("The given user is not the winner", userId);
        }

        try {
            com.topcoder.project.phases.Project projectPhases = managersProvider.getPhaseManager().getPhases(
                    projectId);
            Phase[] phases = projectPhases.getAllPhases();
            // iterate over the phases to find if the type is "Final Fix"
            for (Phase phase : phases) {
                if (phase.getPhaseType() != null && ("Final Fix".equals(phase.getPhaseType().getName()))) {
                    // check if final fix is open checking its the status
                    if (PhaseStatus.OPEN.equals(phase.getPhaseStatus())) {

                        // create a new Upload
                        Upload upload = createUpload(projectId, userId, filename, "Final Fix");

                        String operator = "" + userId;
                        // persist the upload
                        managersProvider.getUploadManager().createUpload(upload, operator);

                        LOG.log(Level.INFO,
                                "Created final fix Upload for project {0}, user {1} with file name {2}.",
                                new Object[] {projectId, userId, filename });

                        // delete the previous submissions
                        SubmissionStatus[] submissionStatus = managersProvider.getUploadManager()
                                .getAllSubmissionStatuses();
                        deletePreviousSubmissions(userId, resource, submissionStatus);
                        LOG.log(Level.INFO, "Marked previous final fixes for deletion for the user {0}.",
                                new Object[] {userId });

                        return upload.getId();

                    } else { // end of if PhaseStatus.OPEN
                        LOG.log(Level.ERROR, "The 'Final Fix' phase is not OPEN for phaseId {0}, userId {1}",
                                new Object[] {phase.getId(), userId });
                        throw new InvalidProjectPhaseException("The 'Final Fix' phase is not OPEN", phase.getId());
                    }
                } // end of if Final Fix

            } // end of for loop
            LOG.log(Level.ERROR, "Failed to upload final fix for the projectId {0}", new Object[] {project
                    .getId() });
            throw new InvalidProjectException("Failed to upload final fix for the project", project.getId());
        } catch (PhaseManagementException e) {
            LOG.log(Level.ERROR, e, "Failed to upload final fix for user {0} and project {1}.", new Object[] {
                userId, projectId });
            throw new UploadServicesException("Failed to upload final fix for user " + userId + " and project "
                    + projectId + ".", e);
        } catch (UploadPersistenceException e) {
            LOG.log(Level.ERROR, e, "Failed to upload final fix for user {0} and project {1}.", new Object[] {
                userId, projectId });
            throw new PersistenceException("Failed to upload final fix for user " + userId + " and project "
                    + projectId + ".", e);
        } finally {
            LOG.log(Level.DEBUG, "Exited DefaultUploadServices#uploadFinalFix(long, long, String)");
        }
    }

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
     * @throws InvalidUserException
     *             if the user does not exist or has not the reviewer role
     * @throws PersistenceException
     *             if some error occurs in persistence layer
     * @throws UploadServicesException
     *             if some other exception occurs in the process (wrap it)
     * @throws IllegalArgumentException
     *             if any id is &lt; 0, if filename is <code>null</code> or trim to empty
     */
    public long uploadTestCases(long projectId, long userId, String filename) throws UploadServicesException {
        LOG.log(Level.DEBUG, "Entered DefaultUploadServices#uploadTestCases(long, long, String)");
        Helper.checkId(projectId, "projectId", LOG);
        Helper.checkId(userId, "userId", LOG);
        Helper.checkString(filename, "filename", LOG);

        // check if the project exists
        Project project = getProject(projectId);

        // check that the user exists and has the reviewer role
        Resource resource = getResource(projectId, userId, new String[] {"Accuracy Reviewer", "Failure Reviewer",
            "Stress Reviewer" });

        try {
            // check that the Review phase is open for the project
            com.topcoder.project.phases.Project projectPhases = managersProvider.getPhaseManager().getPhases(
                    projectId);

            Phase[] phases = projectPhases.getAllPhases();
            // iterate over the phases to find if the type is "Review"
            for (Phase phase : phases) {
                if (phase.getPhaseType() != null && ("Review".equals(phase.getPhaseType().getName()))) {
                    // check if final fix is open checking its the status
                    if (PhaseStatus.OPEN.equals(phase.getPhaseStatus())) {
                        // create a new Upload
                        Upload upload = createUpload(projectId, userId, filename, "Review");

                        String operator = "" + userId;
                        // persist the upload
                        managersProvider.getUploadManager().createUpload(upload, operator);
                        LOG.log(Level.INFO,
                                "Created test cases Upload for project {0}, user {1} with file name {2}.",
                                new Object[] {projectId, userId, filename });

                        // delete the previous submissions
                        SubmissionStatus[] submissionStatus = managersProvider.getUploadManager()
                                .getAllSubmissionStatuses();
                        deletePreviousSubmissions(userId, resource, submissionStatus);
                        LOG.log(Level.INFO, "Marked previous test cases for deletion for the user {0}.",
                                new Object[] {userId });

                        return upload.getId();

                    } else { // end of if PhaseStatus.OPEN
                        LOG.log(Level.ERROR, "The 'Review' phase is not OPEN for phaseId {0}",
                                new Object[] {phase.getId() });
                        throw new InvalidProjectPhaseException("The 'Review' phase is not OPEN", phase.getId());
                    }
                } // end of if Review

            } // end of for loop
            LOG.log(Level.ERROR, "Failed to upload test case for the projectId {0}", new Object[] {project
                    .getId() });
            throw new InvalidProjectException("Failed to upload test case for the project", project.getId());
        } catch (PhaseManagementException e) {
            LOG.log(Level.ERROR, e, "Failed to upload testcases for user {0} and project {1}.", new Object[] {
                userId, projectId });
            throw new UploadServicesException("Failed to upload testcases for user " + userId + " and project "
                    + projectId + ".", e);
        } catch (UploadPersistenceException e) {
            LOG.log(Level.ERROR, e, "Failed to upload testcases for user {0} and project {1}.", new Object[] {
                userId, projectId });
            throw new PersistenceException("Failed to upload testcases for user " + userId + " and project "
                    + projectId + ".", e);
        } finally {
            LOG.log(Level.DEBUG, "Exited DefaultUploadServices#uploadTestCases(long, long, String)");
        }
    }

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
     *             if the submission status does ot exist
     * @throws PersistenceException
     *             if some error occurs in persistence layer
     * @throws IllegalArgumentException
     *             if any id is &lt; 0 or if operator is null or trim to empty
     */
    public void setSubmissionStatus(long submissionId, long submissionStatusId, String operator)
        throws InvalidSubmissionException, InvalidSubmissionStatusException, PersistenceException {
        LOG.log(Level.DEBUG, "Entered DefaultUploadServices#setSubmissionStatus(long, long, String)");
        Helper.checkId(submissionId, "submissionId", LOG);
        Helper.checkId(submissionStatusId, "submissionStatusId", LOG);
        Helper.checkString(operator, "operator", LOG);

        try {
            Submission submission = managersProvider.getUploadManager().getSubmission(submissionId);
            if (submission == null) {
                LOG.log(Level.ERROR, "Failed to get submission with the given Id {0}",
                        new Object[] {submissionId });
                throw new InvalidSubmissionException("Failed to get submission with the given Id", submissionId);
            }

            SubmissionStatus[] submissionStatus = managersProvider.getUploadManager().getAllSubmissionStatuses();
            // iterate over statuses and check which status has the submissionStatusId defined
            for (SubmissionStatus status : submissionStatus) {
                if (status.getId() == submissionStatusId) {
                    submission.setSubmissionStatus(status);
                    managersProvider.getUploadManager().updateSubmission(submission, operator);
                    LOG.log(Level.INFO, "Updated submission {0} using operator {1}.", new Object[] {
                            submission.getId(), operator });
                    return;
                }
            }

            LOG.log(Level.ERROR, "Failed to get submission status with the given id {0}",
                    new Object[] {submissionStatusId });
            throw new InvalidSubmissionStatusException("Failed to get submission status with the given id",
                    submissionStatusId);
        } catch (UploadPersistenceException e) {
            LOG.log(Level.ERROR, e,
                    "Failed to get the submission from persistence, submissionId - {0}, submissionStatusId - {1}",
                    new Object[] {submissionId, submissionStatusId });
            throw new PersistenceException("Failed to get the submission from the persistence", e);
        } finally {
            LOG.log(Level.DEBUG, "Exited DefaultUploadServices#setSubmissionStatus(long, long, String)");
        }
    }

    /**
     * Gets the <code>Project</code> from the persistence.
     *
     * @param projectId
     *            the project id to use
     * @return the <code>Project</code>
     * @throws PersistenceException
     *             if some error occurs in persistence layer
     * @throws InvalidProjectException
     *             if the project does not exist
     */
    private Project getProject(long projectId) throws PersistenceException, InvalidProjectException {
        Project project;
        try {
            project = managersProvider.getProjectManager().getProject(projectId);
        } catch (com.topcoder.management.project.PersistenceException e) {
            LOG.log(Level.ERROR, e, "Failed to get the project with Id {0}", new Object[] {projectId });
            throw new PersistenceException("Failed to get the project with Id " + projectId + ".", e);
        }
        if (project == null) {
            LOG.log(Level.ERROR, "Project does not exist - {0}", new Object[] {projectId });
            throw new InvalidProjectException("Project does not exist", projectId);
        }
        return project;
    }

    /**
     * Gets the resource role id for the given role.
     *
     * @param roles
     *            the roles to use
     * @return the resource role id or zero
     * @throws ResourcePersistenceException
     *             if failed to get resource roles
     */
    private Long[] getSubmitterRoleId(String[] roles) throws ResourcePersistenceException {
        ResourceManager manager = managersProvider.getResourceManager();
        ResourceRole[] resourceRoles = manager.getAllResourceRoles();
        List<Long> resourceRolesIds = new ArrayList<Long>();
        for (ResourceRole resourceRole : resourceRoles) {
            for (String role : roles) {
                if (role.equals(resourceRole.getName())) {
                    // if matched return the resourceRoleId
                    resourceRolesIds.add(resourceRole.getId());
                }
            }
        }
        return (Long[]) resourceRolesIds.toArray(new Long[resourceRolesIds.size()]);
    }

    /**
     * Gets the <code>Resource</code> associated with the project and user with the given role.
     *
     * @param projectId
     *            the project id to use
     * @param userId
     *            the user's id
     * @param roles
     *            the roles for filtering
     * @return the <code>Resource</code>
     * @throws UploadServicesException
     *             if any error occurs
     */
    private Resource getResource(long projectId, long userId, String[] roles) throws UploadServicesException {
        ResourceManager manager = managersProvider.getResourceManager();
        Long[] submitterRoleIds;
        try {
            submitterRoleIds = getSubmitterRoleId(roles);
        } catch (ResourcePersistenceException e) {
            LOG.log(Level.ERROR, e, "Failed to get submitter role ids for the given userId {0}",
                    new Object[] {userId });
            throw new PersistenceException("Failed to get submitter role ids for user " + userId + ".", e);
        }
        if (submitterRoleIds.length == 0) {
            LOG.log(Level.ERROR, "There is no submitterRoleId for the given userId {0}", new Object[] {userId });
            throw new InvalidUserException("There is no submitterRoleId for the given user", userId);
        }

        Filter[] filters = new Filter[submitterRoleIds.length];

        for (int i = 0; i < filters.length; i++) {
            filters[i] = ResourceFilterBuilder.createResourceRoleIdFilter(submitterRoleIds[i]);
        }

        Filter submitterRoleIdFilter = new OrFilter(Arrays.asList(filters));

        // create the filter for searching resources
        AndFilter filter = new AndFilter(Arrays.asList(new Filter[] {submitterRoleIdFilter,
                ResourceFilterBuilder.createProjectIdFilter(projectId),
                ResourceFilterBuilder.createExtensionPropertyNameFilter("External Reference ID"),
                ResourceFilterBuilder.createExtensionPropertyValueFilter("" + userId) }));

        Resource[] resources;
        try {
            resources = manager.searchResources(filter);
        } catch (ResourcePersistenceException e) {
            LOG.log(Level.ERROR, e, "Failed to search resources for for the given userId {0}",
                    new Object[] {userId });
            throw new PersistenceException("Failed to search resources for for the given userId " + userId + ".",
                    e);
        } catch (SearchBuilderException e) {
            LOG.log(Level.ERROR, e, "Failed to search resources for for the given userId {0}",
                    new Object[] {userId });
            throw new UploadServicesException("Failed to search resources for for the given userId " + userId
                    + ".", e);
        }

        if (resources.length != 1) {
            LOG.log(Level.ERROR, "There is no resource for the given userId {0}", new Object[] {userId });
            throw new InvalidUserException("There is no resource for the given user", userId);
        }

        return resources[0];
    }

    /**
     * Creates the <code>Upload</code> and set the required attributes.
     *
     * @param projectId
     *            the project id to use
     * @param userId
     *            the user's id
     * @param filename
     *            the filename to set as parameter
     * @param uploadType
     *            the type of upload
     * @return the created <code>Upload</code> instance
     * @throws PersistenceException
     *             if some error occurs in persistence layer
     */
    private Upload createUpload(long projectId, long userId, String filename, String uploadType)
        throws PersistenceException {
        // create a new Upload
        Upload upload = new Upload();

        // iterate over all UploadStatuses, get the UploadStatus with name "Active" and set to
        // Upload
        try {
            UploadStatus[] uploadStatus = managersProvider.getUploadManager().getAllUploadStatuses();
            for (UploadStatus status : uploadStatus) {
                if ("Active".equals(status.getName())) {
                    upload.setUploadStatus(status);
                    break;
                }
            }

            // iterate over all UploadTypes, get the UploadType with name "Submission" and set to
            // Upload
            UploadType[] uploadTypes = managersProvider.getUploadManager().getAllUploadTypes();
            for (UploadType type : uploadTypes) {
                if (uploadType.equals(type.getName())) {
                    upload.setUploadType(type);
                    break;
                }
            }

            // set the owner as userId
            upload.setOwner(userId);

            // set the projectId
            upload.setProject(projectId);

            // file name have to be passed
            upload.setParameter(filename);

            return upload;

        } catch (UploadPersistenceException e) {
            LOG.log(Level.ERROR, e, "Failed to create Upload properly with projectId {0} and userId {1}",
                    new Object[] {projectId, userId });
            throw new PersistenceException("Failed to create upload properly.", e);
        }

    }

    /**
     * Deletes the previous submissions for the given user.
     *
     * @param userId
     *            the user's id
     * @param resource
     *            the resource to create the filter
     * @param submissionStatus
     *            the submission status to get the Deleted submission status
     * @throws PersistenceException
     *             if some error occurs in persistence layer
     * @throws InvalidSubmissionException
     *             if the submission does not exist
     * @throws InvalidSubmissionStatusException
     *             if the submission status does not exist
     * @throws UploadServicesException
     *             if some other exception occurs in the process (wrap it)
     */
    private void deletePreviousSubmissions(long userId, Resource resource, SubmissionStatus[] submissionStatus)
        throws UploadServicesException {
        try {
            // Change previous submissions status to "Deleted"
            Filter prevSubFilter = SubmissionFilterBuilder.createResourceIdFilter(resource.getId());

            Submission[] prevSubmissions = managersProvider.getUploadManager().searchSubmissions(prevSubFilter);

            // set the statuses of these submission to "Deleted"
            long delSubStatusId = 0;
            for (SubmissionStatus status : submissionStatus) {
                if ("Deleted".equals(status.getName())) {
                    delSubStatusId = status.getId();
                    break;
                }
            }
            String operator = "" + userId;
            for (Submission prevSubmission : prevSubmissions) {
                // set delSubStatusId to submissions using the method in this class
                setSubmissionStatus(prevSubmission.getId(), delSubStatusId, operator);

                // persist the submissions
                managersProvider.getUploadManager().updateSubmission(prevSubmission, operator);
            }
        } catch (UploadPersistenceException e) {
            LOG.log(Level.ERROR, e, "Failed to delete previous submissions for userId {0}",
                    new Object[] {userId });
            throw new PersistenceException("Failed to delete previous submissions.", e);
        } catch (SearchBuilderException e) {
            LOG.log(Level.ERROR, e, "Failed to delete previous submissions for userId {0}",
                    new Object[] {userId });
            throw new UploadServicesException("Failed to delete previous submissions.", e);
        }

    }
}
