/*&silent*/
import groovy.transform.Field
import groovy.json.JsonBuilder
import groovy.time.TimeCategory
import groovy.time.TimeDuration

@Field Date currentDate = new Date()
@Field Date periodDate = use(TimeCategory) { currentDate - 1.minute }

def serviceCallByStates() {
    String hqlServiceCallsByStates = '''
SELECT count(*)
FROM serviceCall sc
WHERE sc.state = :stateCode
'''
    def serviceCallByStates = [:]

    api.metainfo.getMetaClass('serviceCall').workflow.states.code.each { code ->
        serviceCallByStates.put(code,
                api.db.query(hqlServiceCallsByStates)
                        .set('stateCode', code)
                        .list()[0]
                        .toString()
        )
    }

    return serviceCallByStates
}

def periodComments() {
    String hqlPeriodComments = '''
SELECT count(comm)
FROM comment comm
WHERE comm.creationDate between :periodDate and :currentDate
AND comm.author is not null
AND NOT EXISTS(
SELECT id 
FROM employee em
WHERE em.lastName = 'Служебный'
AND em.id = comm.author.id
)
'''

    def comments = api.db.query(hqlPeriodComments)
            .set('periodDate', periodDate)
            .set('currentDate', currentDate)
            .list()[0]
}

def periodServiceCalls() {
    String hqlPeriodServiceCalls = '''
SELECT 'serviceCall$'||sc.id, 'employee$'||sc.author.id, sc.requestDate, sc.registrationDate, sc.wayAddressing.code
FROM serviceCall sc
WHERE sc.creationDate between :periodDate and :currentDate
'''

    api.db.query(hqlPeriodServiceCalls)
            .set('periodDate', periodDate)
            .set('currentDate', currentDate)
            .list().collect { row ->
        return [
                'UUID'            : row[0],
                'author'          : row[1],
                'requestDate'     : row[2],
                'registrationDate': row[3],
                //'registrationTime': TimeCategory.minus(row[2], row[3]),
                'registrationTime': row[2] - row[3],
                'wayAddressing'   : row[4]
        ]
    }
}

def periodAdminActions() {
    String hqlPeriodAdminLogs = '''
SELECT 'adminLogRecord$'||log.id, log.authorLogin, log.category
FROM adminLogRecord log
WHERE log.actionDate between :periodDate and :currentDate
AND log.authorLogin is not 'naumen'
'''

    api.db.query(hqlPeriodAdminLogs)
            .set('periodDate', periodDate)
            .set('currentDate', currentDate)
            .list().collect { row ->
        return [
                'UUID'    : row[0],
                'author'  : row[1],
                'category': row[2]
        ]
    }
}

def business_metrics = [
        'itsm365_comments_for_the_period'    : periodComments(),
        'itsm365_serviceCalls_for_the_period': periodServiceCalls(),
        'itsm365_license_usage_per_user'     : api.employee.getLicensesUsage(),
        'itsm365_serviceCalls_per_states'    : serviceCallByStates(),
        'itsm365_admin_logs_for_the_period'  : periodAdminActions()
]

return new JsonBuilder(business_metrics).toPrettyString()